package org.example.voicecampaign.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.voicecampaign.domain.entity.Campaign;
import org.example.voicecampaign.domain.model.CallStatus;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RemainingCallsSchedulingStrategy implements SchedulingStrategy {

    private final CallRequestRepository callRequestRepository;
    
    private final Map<java.util.UUID, Long> remainingCallsCache = new HashMap<>();

    @Override
    public List<Campaign> prioritizeCampaigns(List<Campaign> campaigns) {
        // Cache remaining calls count
        for (Campaign campaign : campaigns) {
            long pending = callRequestRepository.countByCampaignIdAndStatus(
                    campaign.getId(), CallStatus.PENDING);
            long failed = callRequestRepository.countByCampaignIdAndStatus(
                    campaign.getId(), CallStatus.FAILED);
            remainingCallsCache.put(campaign.getId(), pending + failed);
        }
        
        // Prioritize campaigns with fewer remaining calls (to complete them faster)
        return campaigns.stream()
                .sorted(Comparator
                        .comparingLong((Campaign c) -> remainingCallsCache.getOrDefault(c.getId(), 0L))
                        .thenComparingInt(Campaign::getPriority).reversed())
                .toList();
    }

    @Override
    public int calculateSlotsForCampaign(Campaign campaign, int availableSlots, int totalActiveCampaigns) {
        long remainingCalls = remainingCallsCache.getOrDefault(campaign.getId(), 0L);
        
        // Don't allocate more slots than remaining calls
        int neededSlots = (int) Math.min(remainingCalls, campaign.getConcurrencyLimit());
        return Math.min(neededSlots, availableSlots);
    }
}
