package org.example.voicecampaign.scheduler.strategy;

import lombok.Builder;
import lombok.Getter;
import org.example.voicecampaign.domain.entity.Campaign;

import java.util.Map;
import java.util.UUID;

/**
 * Context object providing additional information for scheduling decisions.
 */
@Getter
@Builder
public class SchedulingContext {

    private final Map<UUID, Long> remainingCallsPerCampaign;
    private final Map<UUID, Integer> activeSlotsPerCampaign;
    private final Map<UUID, Integer> queuedCountPerCampaign;

    public long getRemainingCalls(Campaign campaign) {
        return remainingCallsPerCampaign.getOrDefault(campaign.getId(), 0L);
    }

    public int getActiveSlots(Campaign campaign) {
        return activeSlotsPerCampaign.getOrDefault(campaign.getId(), 0);
    }

    public int getQueuedCount(Campaign campaign) {
        return queuedCountPerCampaign.getOrDefault(campaign.getId(), 0);
    }

    public int getAvailableSlots(Campaign campaign) {
        int active = getActiveSlots(campaign);
        int queued = getQueuedCount(campaign);
        return Math.max(0, campaign.getConcurrencyLimit() - active - queued);
    }
}
