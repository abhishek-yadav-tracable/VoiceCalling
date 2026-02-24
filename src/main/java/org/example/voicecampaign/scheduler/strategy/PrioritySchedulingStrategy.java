package org.example.voicecampaign.scheduler.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.Campaign;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Priority-based distribution strategy.
 * 
 * <p>Allocates more slots to higher-priority campaigns. Priority 10 campaigns
 * receive proportionally more slots than priority 1 campaigns.</p>
 */
@Component
@Slf4j
public class PrioritySchedulingStrategy implements SchedulingStrategy {

    @Override
    public String getName() {
        return "priority";
    }

    @Override
    public Map<Campaign, Integer> distribute(List<Campaign> campaigns, int totalSlots, SchedulingContext context) {
        Map<Campaign, Integer> allocation = new HashMap<>();
        
        if (campaigns.isEmpty() || totalSlots <= 0) {
            return allocation;
        }

        // Sort by priority descending
        List<Campaign> sortedCampaigns = campaigns.stream()
                .sorted(Comparator.comparingInt(Campaign::getPriority).reversed())
                .toList();

        // Calculate total priority weight
        int totalPriority = sortedCampaigns.stream()
                .mapToInt(Campaign::getPriority)
                .sum();

        if (totalPriority == 0) {
            totalPriority = sortedCampaigns.size(); // Fallback to equal distribution
        }

        int remainingSlots = totalSlots;
        
        for (Campaign campaign : sortedCampaigns) {
            if (remainingSlots <= 0) break;
            
            int availableSlots = context.getAvailableSlots(campaign);
            if (availableSlots <= 0) continue;

            // Proportional allocation based on priority
            int proportionalSlots = (int) Math.ceil((double) campaign.getPriority() / totalPriority * totalSlots);
            int allocated = Math.min(Math.min(proportionalSlots, availableSlots), remainingSlots);
            
            // Ensure at least 1 slot for each campaign with capacity
            allocated = Math.max(1, allocated);
            allocated = Math.min(allocated, Math.min(availableSlots, remainingSlots));
            
            if (allocated > 0) {
                allocation.put(campaign, allocated);
                remainingSlots -= allocated;
            }
        }

        log.debug("Priority: distributed {} slots across {} campaigns (by priority)", 
                allocation.values().stream().mapToInt(Integer::intValue).sum(), 
                allocation.size());
        
        return allocation;
    }
}
