package org.example.voicecampaign.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.CallRequest;
import org.example.voicecampaign.domain.entity.Campaign;
import org.example.voicecampaign.domain.model.CallStatus;
import org.example.voicecampaign.domain.model.CampaignStatus;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.example.voicecampaign.repository.CampaignRepository;
import org.example.voicecampaign.scheduler.strategy.SchedulingContext;
import org.example.voicecampaign.scheduler.strategy.SchedulingStrategy;
import org.example.voicecampaign.scheduler.strategy.SchedulingStrategyFactory;
import org.example.voicecampaign.service.CampaignMetricsService;
import org.example.voicecampaign.worker.CallWorkerPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduler that enqueues pending and retryable calls to the Redis queue for processing.
 * 
 * <p>Runs on a fixed interval and performs fair distribution of calls across active campaigns,
 * respecting per-campaign concurrency limits and business hours.</p>
 * 
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Query DB for active campaigns within business hours</li>
 *   <li>Fetch PENDING and FAILED (retryable) calls</li>
 *   <li>Push call IDs to Redis queue for worker consumption</li>
 *   <li>Track queued counts per campaign to respect concurrency limits</li>
 * </ul>
 */
@Service
@Slf4j
public class CallQueueService {

    private final CampaignRepository campaignRepository;
    private final CallRequestRepository callRequestRepository;
    private final CampaignMetricsService metricsService;
    private final CallWorkerPool workerPool;
    private final StringRedisTemplate redisTemplate;
    private final SchedulingStrategyFactory strategyFactory;

    @Value("${voice-campaign.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${voice-campaign.scheduler.batch-size:100}")
    private int batchSize;

    @Value("${voice-campaign.worker.max-queue-depth:1000}")
    private int maxQueueDepth;

    private static final String CAMPAIGN_QUEUED_KEY = "campaign:%s:queued";

    public CallQueueService(
            CampaignRepository campaignRepository,
            CallRequestRepository callRequestRepository,
            CampaignMetricsService metricsService,
            CallWorkerPool workerPool,
            StringRedisTemplate redisTemplate,
            SchedulingStrategyFactory strategyFactory) {
        this.campaignRepository = campaignRepository;
        this.callRequestRepository = callRequestRepository;
        this.metricsService = metricsService;
        this.workerPool = workerPool;
        this.redisTemplate = redisTemplate;
        this.strategyFactory = strategyFactory;
    }

    /**
     * Scheduled task that enqueues calls from active campaigns to the Redis queue.
     * Runs at a fixed rate configured by {@code voice-campaign.scheduler.fixed-rate-ms}.
     */
    @Scheduled(fixedRateString = "${voice-campaign.scheduler.fixed-rate-ms:1000}")
    public void enqueueCallsForActiveCampaigns() {
        if (!schedulerEnabled) {
            return;
        }

        // Check current queue depth - don't over-fill the queue
        long currentQueueDepth = workerPool.getQueueDepth();
        if (currentQueueDepth >= maxQueueDepth) {
            log.debug("Queue is full ({}/{}), skipping enqueue cycle", currentQueueDepth, maxQueueDepth);
            return;
        }

        int slotsToFill = (int) Math.min(batchSize, maxQueueDepth - currentQueueDepth);

        List<Campaign> activeCampaigns = campaignRepository.findSchedulableCampaigns();
        
        if (activeCampaigns.isEmpty()) {
            return;
        }

        // Filter campaigns within business hours - even IN_PROGRESS campaigns must respect business hours
        List<Campaign> eligibleCampaigns = activeCampaigns.stream()
                .filter(c -> {
                    boolean withinHours = c.getBusinessHours() == null || c.getBusinessHours().isWithinBusinessHours();
                    if (!withinHours) {
                        log.debug("Campaign {} is outside business hours (timezone: {}), skipping", 
                                c.getId(), c.getBusinessHours() != null ? c.getBusinessHours().getTimezone() : "N/A");
                    }
                    return withinHours;
                })
                .toList();

        if (eligibleCampaigns.isEmpty()) {
            if (!activeCampaigns.isEmpty()) {
                log.debug("All {} active campaigns are outside their business hours", activeCampaigns.size());
            }
            return;
        }

        // Build scheduling context with metrics for each campaign
        SchedulingContext context = buildSchedulingContext(eligibleCampaigns);

        // Use pluggable strategy for slot distribution
        SchedulingStrategy strategy = strategyFactory.getStrategy();
        Map<Campaign, Integer> allocation = strategy.distribute(eligibleCampaigns, slotsToFill, context);
        
        for (Map.Entry<Campaign, Integer> entry : allocation.entrySet()) {
            Campaign campaign = entry.getKey();
            int allocatedSlots = entry.getValue();
            try {
                enqueueCallsForCampaign(campaign, allocatedSlots);
            } catch (Exception e) {
                log.error("Error enqueuing calls for campaign {}: {}", campaign.getId(), e.getMessage());
            }
        }
    }

    private SchedulingContext buildSchedulingContext(List<Campaign> campaigns) {
        Map<UUID, Long> remainingCalls = new HashMap<>();
        Map<UUID, Integer> activeSlots = new HashMap<>();
        Map<UUID, Integer> queuedCounts = new HashMap<>();

        for (Campaign campaign : campaigns) {
            UUID id = campaign.getId();
            
            // Count remaining calls (PENDING + FAILED that can be retried)
            long pending = callRequestRepository.countByCampaignIdAndStatus(id, CallStatus.PENDING);
            long failed = callRequestRepository.countByCampaignIdAndStatus(id, CallStatus.FAILED);
            remainingCalls.put(id, pending + failed);
            
            activeSlots.put(id, metricsService.getActiveSlots(id));
            queuedCounts.put(id, getQueuedCount(id));
        }

        return SchedulingContext.builder()
                .remainingCallsPerCampaign(remainingCalls)
                .activeSlotsPerCampaign(activeSlots)
                .queuedCountPerCampaign(queuedCounts)
                .build();
    }

    @org.springframework.transaction.annotation.Transactional
    protected void enqueueCallsForCampaign(Campaign campaign, int maxCalls) {
        UUID campaignId = campaign.getId();
        
        // Check concurrency limit
        int currentSlots = metricsService.getActiveSlots(campaignId);
        int queuedCount = getQueuedCount(campaignId);
        int availableSlots = campaign.getConcurrencyLimit() - currentSlots - queuedCount;

        if (availableSlots <= 0) {
            log.debug("Campaign {} has no available slots (active={}, queued={}, limit={})", 
                    campaignId, currentSlots, queuedCount, campaign.getConcurrencyLimit());
            return;
        }

        int toEnqueue = Math.min(maxCalls, availableSlots);
        int enqueued = 0;

        // Prioritize retries over new calls
        enqueued += enqueueRetries(campaign, toEnqueue);
        
        if (enqueued < toEnqueue) {
            enqueued += enqueuePendingCalls(campaign, toEnqueue - enqueued);
        }

        // Update campaign status if needed
        if (enqueued > 0 && campaign.getStatus() == CampaignStatus.PENDING) {
            campaign.setStatus(CampaignStatus.IN_PROGRESS);
            campaignRepository.save(campaign);
        }

        if (enqueued > 0) {
            log.debug("Enqueued {} calls for campaign {}", enqueued, campaignId);
        }
    }

    private int enqueueRetries(Campaign campaign, int maxCalls) {
        Instant now = Instant.now();
        List<CallRequest> retryableCalls = callRequestRepository.findRetryableCallsForCampaign(
                campaign.getId(), now, PageRequest.of(0, maxCalls));

        int enqueued = 0;
        for (CallRequest callRequest : retryableCalls) {
            enqueue(campaign.getId(), callRequest.getId());
            enqueued++;
        }
        return enqueued;
    }

    private int enqueuePendingCalls(Campaign campaign, int maxCalls) {
        List<CallRequest> pendingCalls = callRequestRepository.findPendingCallsForCampaign(
                campaign.getId(), PageRequest.of(0, maxCalls));

        int enqueued = 0;
        for (CallRequest callRequest : pendingCalls) {
            enqueue(campaign.getId(), callRequest.getId());
            enqueued++;
        }
        return enqueued;
    }

    private void enqueue(UUID campaignId, UUID callRequestId) {
        incrementQueuedCount(campaignId);
        workerPool.enqueueCall(callRequestId);
    }

    private int getQueuedCount(UUID campaignId) {
        String key = String.format(CAMPAIGN_QUEUED_KEY, campaignId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid queued count value for campaign {}: {}", campaignId, value);
            return 0;
        }
    }

    private void incrementQueuedCount(UUID campaignId) {
        String key = String.format(CAMPAIGN_QUEUED_KEY, campaignId);
        redisTemplate.opsForValue().increment(key);
    }

}
