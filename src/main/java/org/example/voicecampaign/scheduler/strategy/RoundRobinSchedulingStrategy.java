package org.example.voicecampaign.scheduler.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.Campaign;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fair round-robin distribution strategy.
 * 
 * <p>Distributes slots equally across all campaigns, respecting each campaign's
 * available capacity. This ensures no single campaign monopolizes resources.</p>
 */
@Component
@Slf4j
public class RoundRobinSchedulingStrategy implements SchedulingStrategy {

    @Override
    public String getName() {
        return "round-robin";
    }

    @Override
    public Map<Campaign, Integer> distribute(List<Campaign> campaigns, int totalSlots, SchedulingContext context) {
        Map<Campaign, Integer> allocation = new HashMap<>();
        
        if (campaigns.isEmpty() || totalSlots <= 0) {
            return allocation;
        }

        int slotsPerCampaign = Math.max(1, totalSlots / campaigns.size());
        
        for (Campaign campaign : campaigns) {
            int availableSlots = context.getAvailableSlots(campaign);
            int allocated = Math.min(slotsPerCampaign, availableSlots);
            
            if (allocated > 0) {
                allocation.put(campaign, allocated);
            }
        }

        log.debug("RoundRobin: distributed {} slots across {} campaigns", 
                allocation.values().stream().mapToInt(Integer::intValue).sum(), 
                allocation.size());
        
        return allocation;
    }
}
