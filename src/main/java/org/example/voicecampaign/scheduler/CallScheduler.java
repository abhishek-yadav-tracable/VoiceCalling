package org.example.voicecampaign.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.CallRequest;
import org.example.voicecampaign.domain.entity.Campaign;
import org.example.voicecampaign.domain.model.CampaignStatus;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.example.voicecampaign.repository.CampaignRepository;
import org.example.voicecampaign.service.CallService;
import org.example.voicecampaign.service.CampaignMetricsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * @deprecated Replaced by CallQueueService + CallWorkerPool for proper worker pool architecture.
 * This scheduler is disabled. The new architecture uses:
 * - CallQueueService: Enqueues calls to Redis queue
 * - CallWorkerPool: Fixed thread pool that processes calls from the queue
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CallScheduler {

    private final CampaignRepository campaignRepository;
    private final CallRequestRepository callRequestRepository;
    private final CampaignMetricsService metricsService;
    private final CallService callService;
    private final RoundRobinSchedulingStrategy schedulingStrategy;

    @Value("${voice-campaign.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${voice-campaign.scheduler.batch-size:100}")
    private int batchSize;

    // Disabled - replaced by CallQueueService
    // @Scheduled(fixedRateString = "${voice-campaign.scheduler.fixed-rate-ms:1000}")
    public void scheduleCallsForActiveCampaigns() {
        // This method is disabled. See CallQueueService for the new implementation.
    }
    
    /*
    // Original implementation - kept for reference
    public void scheduleCallsForActiveCampaignsOld() {
        if (!schedulerEnabled) {
            return;
        }

        List<Campaign> activeCampaigns = campaignRepository.findSchedulableCampaigns();
        
        if (activeCampaigns.isEmpty()) {
            return;
        }

        // Filter campaigns within business hours
        List<Campaign> eligibleCampaigns = activeCampaigns.stream()
                .filter(c -> c.getBusinessHours() == null || c.getBusinessHours().isWithinBusinessHours())
                .toList();

        if (eligibleCampaigns.isEmpty()) {
            log.debug("No campaigns within business hours");
            return;
        }

        // Prioritize campaigns
        List<Campaign> prioritizedCampaigns = schedulingStrategy.prioritizeCampaigns(eligibleCampaigns);

        for (Campaign campaign : prioritizedCampaigns) {
            try {
                scheduleCallsForCampaign(campaign);
            } catch (Exception e) {
                log.error("Error scheduling calls for campaign {}: {}", campaign.getId(), e.getMessage());
            }
        }
    }

    private void scheduleCallsForCampaign(Campaign campaign) {
        int currentSlots = metricsService.getActiveSlots(campaign.getId());
        int availableSlots = campaign.getConcurrencyLimit() - currentSlots;

        if (availableSlots <= 0) {
            log.debug("Campaign {} has no available slots ({}/{})", 
                    campaign.getId(), currentSlots, campaign.getConcurrencyLimit());
            return;
        }

        // Prioritize retries over new calls
        int slotsUsed = scheduleRetries(campaign, availableSlots);
        availableSlots -= slotsUsed;

        if (availableSlots > 0) {
            schedulePendingCalls(campaign, availableSlots);
        }

        // Update campaign status if needed
        if (campaign.getStatus() == CampaignStatus.PENDING) {
            campaign.setStatus(CampaignStatus.IN_PROGRESS);
            campaignRepository.save(campaign);
        }
    }

    private int scheduleRetries(Campaign campaign, int maxSlots) {
        Instant now = Instant.now();
        List<CallRequest> retryableCalls = callRequestRepository.findRetryableCallsForCampaign(
                campaign.getId(), now, PageRequest.of(0, maxSlots));

        int scheduled = 0;
        for (CallRequest callRequest : retryableCalls) {
            if (metricsService.tryAcquireSlot(campaign.getId(), campaign.getConcurrencyLimit())) {
                try {
                    callService.executeCall(callRequest);
                    scheduled++;
                } catch (Exception e) {
                    metricsService.releaseSlot(campaign.getId());
                    log.error("Failed to execute retry for call {}: {}", callRequest.getId(), e.getMessage());
                }
            } else {
                break;
            }
        }

        if (scheduled > 0) {
            log.debug("Scheduled {} retries for campaign {}", scheduled, campaign.getId());
        }

        return scheduled;
    }

    private int schedulePendingCalls(Campaign campaign, int maxSlots) {
        List<CallRequest> pendingCalls = callRequestRepository.findPendingCallsForCampaign(
                campaign.getId(), PageRequest.of(0, maxSlots));

        int scheduled = 0;
        for (CallRequest callRequest : pendingCalls) {
            if (metricsService.tryAcquireSlot(campaign.getId(), campaign.getConcurrencyLimit())) {
                try {
                    callService.executeCall(callRequest);
                    scheduled++;
                } catch (Exception e) {
                    metricsService.releaseSlot(campaign.getId());
                    log.error("Failed to execute call {}: {}", callRequest.getId(), e.getMessage());
                }
            } else {
                break;
            }
        }

        if (scheduled > 0) {
            log.debug("Scheduled {} new calls for campaign {}", scheduled, campaign.getId());
        }

        return scheduled;
    }
    */
}
