package org.example.voicecampaign.scheduler;

import org.example.voicecampaign.domain.entity.Campaign;

import java.util.List;

public interface SchedulingStrategy {
    
    List<Campaign> prioritizeCampaigns(List<Campaign> campaigns);
    
    int calculateSlotsForCampaign(Campaign campaign, int availableSlots, int totalActiveCampaigns);
}
