package org.example.voicecampaign.worker;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.CallRequest;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.example.voicecampaign.service.CallService;
import org.example.voicecampaign.service.CampaignMetricsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallWorkerPool {

    private final CallService callService;
    private final CampaignMetricsService metricsService;
    private final CallRequestRepository callRequestRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${voice-campaign.worker.pool-size:20}")
    private int poolSize;

    @Value("${voice-campaign.worker.queue-poll-timeout-ms:1000}")
    private long queuePollTimeoutMs;

    private static final String CALL_QUEUE_KEY = "call:queue";
    private static final String WORKER_ACTIVE_COUNT_KEY = "worker:active_count";

    private ExecutorService workerPool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        workerPool = Executors.newFixedThreadPool(poolSize);
        running.set(true);
        
        // Initialize active count in Redis
        redisTemplate.opsForValue().set(WORKER_ACTIVE_COUNT_KEY, "0");
        
        // Start worker threads
        for (int i = 0; i < poolSize; i++) {
            final int workerId = i;
            workerPool.submit(() -> runWorker(workerId));
        }
        
        log.info("Started CallWorkerPool with {} workers", poolSize);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Initiating graceful shutdown of CallWorkerPool...");
        
        // 1. Stop accepting new work
        running.set(false);
        
        // 2. Wait for in-flight calls to complete (up to 60 seconds)
        int maxWaitSeconds = 60;
        int waited = 0;
        while (activeWorkers.get() > 0 && waited < maxWaitSeconds) {
            log.info("Waiting for {} active workers to complete...", activeWorkers.get());
            try {
                Thread.sleep(1000);
                waited++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        if (activeWorkers.get() > 0) {
            log.warn("Forcing shutdown with {} workers still active", activeWorkers.get());
        }
        
        // 3. Shutdown the thread pool
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Workers did not terminate in time, forcing shutdown");
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 4. Clear worker active count in Redis
        redisTemplate.delete(WORKER_ACTIVE_COUNT_KEY);
        
        log.info("CallWorkerPool shutdown complete");
    }

    private void runWorker(int workerId) {
        log.debug("Worker {} started", workerId);
        
        while (running.get()) {
            try {
                // Block and wait for a job from the queue
                String callRequestId = redisTemplate.opsForList().rightPop(
                        CALL_QUEUE_KEY, queuePollTimeoutMs, TimeUnit.MILLISECONDS);
                
                if (callRequestId == null) {
                    // No job available, continue polling
                    continue;
                }

                // Mark worker as active
                incrementActiveWorkers();
                
                try {
                    processCall(workerId, UUID.fromString(callRequestId));
                } finally {
                    decrementActiveWorkers();
                }
                
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Worker {} error: {}", workerId, e.getMessage(), e);
                }
            }
        }
        
        log.debug("Worker {} stopped", workerId);
    }

    private void processCall(int workerId, UUID callRequestId) {
        long startTime = System.currentTimeMillis();
        
        // Time DB fetch
        long dbStart = System.currentTimeMillis();
        Optional<CallRequest> callRequestOpt = callRequestRepository.findByIdWithCampaign(callRequestId);
        long dbTime = System.currentTimeMillis() - dbStart;
        
        if (callRequestOpt.isEmpty()) {
            log.warn("Worker {} call {} not found, skipping", workerId, callRequestId);
            return;
        }
        
        CallRequest callRequest = callRequestOpt.get();
        UUID campaignId = callRequest.getCampaign().getId();
        
        // Time Redis operations
        long redisStart = System.currentTimeMillis();
        decrementQueuedCount(campaignId);
        metricsService.incrementActiveSlots(campaignId);
        long redisTime = System.currentTimeMillis() - redisStart;
        
        // Time call execution
        long execStart = System.currentTimeMillis();
        try {
            callService.executeCall(callRequest);
        } catch (Exception e) {
            log.error("Worker {} failed to execute call {}: {}", workerId, callRequestId, e.getMessage());
            metricsService.releaseSlot(campaignId);
        }
        long execTime = System.currentTimeMillis() - execStart;
        
        long totalTime = System.currentTimeMillis() - startTime;
        if (totalTime > 100) { // Log slow calls (>100ms)
            log.warn("SLOW CALL: total={}ms, db={}ms, redis={}ms, exec={}ms", totalTime, dbTime, redisTime, execTime);
        }
    }
    
    private void decrementQueuedCount(UUID campaignId) {
        String key = String.format("campaign:%s:queued", campaignId);
        Long newValue = redisTemplate.opsForValue().decrement(key);
        if (newValue != null && newValue < 0) {
            redisTemplate.opsForValue().set(key, "0");
        }
    }

    /**
     * Enqueue a call request for processing by the worker pool
     */
    public void enqueueCall(UUID callRequestId) {
        redisTemplate.opsForList().leftPush(CALL_QUEUE_KEY, callRequestId.toString());
        log.debug("Enqueued call {}", callRequestId);
    }

    /**
     * Get the current queue depth
     */
    public long getQueueDepth() {
        Long size = redisTemplate.opsForList().size(CALL_QUEUE_KEY);
        return size != null ? size : 0;
    }

    /**
     * Get the number of currently active workers
     */
    public int getActiveWorkerCount() {
        return activeWorkers.get();
    }

    /**
     * Get the total worker pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    private void incrementActiveWorkers() {
        activeWorkers.incrementAndGet();
        redisTemplate.opsForValue().increment(WORKER_ACTIVE_COUNT_KEY);
    }

    private void decrementActiveWorkers() {
        activeWorkers.decrementAndGet();
        redisTemplate.opsForValue().decrement(WORKER_ACTIVE_COUNT_KEY);
    }
}
