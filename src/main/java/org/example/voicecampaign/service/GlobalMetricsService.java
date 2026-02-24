package org.example.voicecampaign.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.model.CallStatus;
import org.example.voicecampaign.domain.model.CampaignStatus;
import org.example.voicecampaign.dto.GlobalMetricsResponse;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.example.voicecampaign.repository.CampaignRepository;
import org.example.voicecampaign.worker.CallWorkerPool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlobalMetricsService {

    private final CampaignRepository campaignRepository;
    private final CallRequestRepository callRequestRepository;
    private final StringRedisTemplate redisTemplate;
    private final CallWorkerPool workerPool;

    private static final String SLOTS_KEY_PREFIX = "campaign:slots:";

    private volatile long metricsStartTime = System.currentTimeMillis();

    public GlobalMetricsResponse getGlobalMetrics() {
        // Campaign counts
        long totalCampaigns = campaignRepository.count();
        long activeCampaigns = campaignRepository.countByStatus(CampaignStatus.IN_PROGRESS);
        long completedCampaigns = campaignRepository.countByStatus(CampaignStatus.COMPLETED);

        // Call counts by status
        long totalCalls = callRequestRepository.count();
        long pendingCalls = callRequestRepository.countByStatus(CallStatus.PENDING);
        long inProgressCalls = callRequestRepository.countByStatus(CallStatus.IN_PROGRESS);
        long completedCalls = callRequestRepository.countByStatus(CallStatus.COMPLETED);
        long failedCalls = callRequestRepository.countByStatus(CallStatus.FAILED);
        long permanentlyFailedCalls = callRequestRepository.countByStatus(CallStatus.PERMANENTLY_FAILED);

        // Calculate total retries
        Long totalRetries = callRequestRepository.sumRetryCount();
        if (totalRetries == null) totalRetries = 0L;

        // Worker thread pool metrics
        int workerPoolSize = workerPool.getPoolSize();
        int activeWorkerThreads = workerPool.getActiveWorkerCount();
        long queueDepth = workerPool.getQueueDepth();
        double workerThreadUtilization = workerPoolSize > 0 
                ? (activeWorkerThreads * 100.0 / workerPoolSize) 
                : 0.0;
        
        // Concurrency slot metrics (calls in progress vs total allowed)
        Integer totalConcurrencyLimit = campaignRepository.sumConcurrencyLimitForActiveCampaigns();
        int totalConcurrencySlots = totalConcurrencyLimit != null ? totalConcurrencyLimit : 0;
        int activeConcurrencySlots = (int) inProgressCalls;
        double concurrencyUtilization = totalConcurrencySlots > 0 
                ? (activeConcurrencySlots * 100.0 / totalConcurrencySlots) 
                : 0.0;

        // Throughput calculation
        long elapsedSeconds = Math.max(1, (System.currentTimeMillis() - metricsStartTime) / 1000);
        double callsPerSecond = completedCalls > 0 ? (double) completedCalls / elapsedSeconds : 0.0;
        
        // Average call duration
        Double avgDuration = callRequestRepository.avgCallDuration();
        double avgCallDurationSeconds = avgDuration != null ? avgDuration : 0.0;

        return GlobalMetricsResponse.builder()
                .totalCampaigns((int) totalCampaigns)
                .activeCampaigns((int) activeCampaigns)
                .completedCampaigns((int) completedCampaigns)
                .totalCalls(totalCalls)
                .pendingCalls(pendingCalls)
                .inProgressCalls(inProgressCalls)
                .completedCalls(completedCalls)
                .failedCalls(failedCalls)
                .permanentlyFailedCalls(permanentlyFailedCalls)
                .totalRetries(totalRetries)
                // Worker thread pool
                .workerPoolSize(workerPoolSize)
                .activeWorkerThreads(activeWorkerThreads)
                .workerThreadUtilizationPercent(Math.round(workerThreadUtilization * 100.0) / 100.0)
                .queueDepth(queueDepth)
                // Concurrency slots
                .totalConcurrencySlots(totalConcurrencySlots)
                .activeConcurrencySlots(activeConcurrencySlots)
                .concurrencyUtilizationPercent(Math.round(concurrencyUtilization * 100.0) / 100.0)
                // Throughput
                .callsPerSecond(Math.round(callsPerSecond * 100.0) / 100.0)
                .avgCallDurationSeconds(Math.round(avgCallDurationSeconds * 100.0) / 100.0)
                .build();
    }

    private int getTotalWorkerSlots() {
        // Sum of concurrency limits from all active (IN_PROGRESS) campaigns
        Integer total = campaignRepository.sumConcurrencyLimitForActiveCampaigns();
        // If no active campaigns, return 0 (utilization will show as N/A or 0%)
        return total != null ? total : 0;
    }

    private int getActiveWorkerSlots() {
        // Count all active slots from Redis
        Set<String> keys = redisTemplate.keys(SLOTS_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        
        int total = 0;
        for (String key : keys) {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                total += Integer.parseInt(value.toString());
            }
        }
        return total;
    }

    public void resetMetricsStartTime() {
        metricsStartTime = System.currentTimeMillis();
    }
}
