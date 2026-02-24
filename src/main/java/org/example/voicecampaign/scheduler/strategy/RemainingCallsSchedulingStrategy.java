package org.example.voicecampaign.scheduler.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.Campaign;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remaining calls based distribution strategy.
 * 
 * <p>Prioritizes campaigns that are closer to completion (fewer remaining calls).
 * This helps finish campaigns faster and free up resources.</p>
 */
@Component
@Slf4j
public class RemainingCallsSchedulingStrategy implements SchedulingStrategy {

    @Override
    public String getName() {
        return "remaining-calls";
    }

    @Override
    public Map<Campaign, Integer> distribute(List<Campaign> campaigns, int totalSlots, SchedulingContext context) {
        Map<Campaign, Integer> allocation = new HashMap<>();
        
        if (campaigns.isEmpty() || totalSlots <= 0) {
            return allocation;
        }

        // Sort by remaining calls ascending (campaigns closer to completion first)
        List<Campaign> sortedCampaigns = campaigns.stream()
                .sorted(Comparator.comparingLong(context::getRemainingCalls))
                .toList();

        int remainingSlots = totalSlots;
        int campaignsWithCapacity = (int) sortedCampaigns.stream()
                .filter(c -> context.getAvailableSlots(c) > 0)
                .count();

        if (campaignsWithCapacity == 0) {
            return allocation;
        }

        // Give more slots to campaigns closer to completion
        for (int i = 0; i < sortedCampaigns.size() && remainingSlots > 0; i++) {
            Campaign campaign = sortedCampaigns.get(i);
            int availableSlots = context.getAvailableSlots(campaign);
            
            if (availableSlots <= 0) continue;

            // Campaigns closer to completion (lower index) get more slots
            // Weight decreases as we go through the list
            double weight = (double) (sortedCampaigns.size() - i) / sortedCampaigns.size();
            int baseSlots = Math.max(1, (int) Math.ceil(totalSlots * weight / campaignsWithCapacity));
            int allocated = Math.min(Math.min(baseSlots, availableSlots), remainingSlots);
            
            if (allocated > 0) {
                allocation.put(campaign, allocated);
                remainingSlots -= allocated;
            }
        }

        log.debug("RemainingCalls: distributed {} slots across {} campaigns (prioritizing near-completion)", 
                allocation.values().stream().mapToInt(Integer::intValue).sum(), 
                allocation.size());
        
        return allocation;
    }
}
