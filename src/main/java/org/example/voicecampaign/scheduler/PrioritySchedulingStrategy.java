package org.example.voicecampaign.scheduler;

import org.example.voicecampaign.domain.entity.Campaign;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class PrioritySchedulingStrategy implements SchedulingStrategy {

    @Override
    public List<Campaign> prioritizeCampaigns(List<Campaign> campaigns) {
        return campaigns.stream()
                .sorted(Comparator
                        .comparingInt(Campaign::getPriority).reversed()
                        .thenComparing(Campaign::getCreatedAt))
                .toList();
    }

    @Override
    public int calculateSlotsForCampaign(Campaign campaign, int availableSlots, int totalActiveCampaigns) {
        // Priority-based: higher priority campaigns get more slots
        // Priority 10 gets 100% of their limit, priority 1 gets 10%
        double priorityFactor = campaign.getPriority() / 10.0;
        int desiredSlots = (int) Math.ceil(campaign.getConcurrencyLimit() * priorityFactor);
        return Math.min(desiredSlots, availableSlots);
    }
}
