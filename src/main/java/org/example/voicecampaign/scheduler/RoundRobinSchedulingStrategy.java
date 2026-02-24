package org.example.voicecampaign.scheduler;

import org.example.voicecampaign.domain.entity.Campaign;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class RoundRobinSchedulingStrategy implements SchedulingStrategy {

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
        if (totalActiveCampaigns == 0) {
            return 0;
        }
        
        int fairShare = Math.max(1, availableSlots / totalActiveCampaigns);
        return Math.min(fairShare, campaign.getConcurrencyLimit());
    }
}
