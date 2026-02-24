package org.example.voicecampaign.scheduler.strategy;

import org.example.voicecampaign.domain.entity.Campaign;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for distributing call slots across campaigns.
 * 
 * <p>Implementations determine how many calls each campaign should receive
 * based on different criteria (fairness, priority, remaining calls, etc.).</p>
 */
public interface SchedulingStrategy {

    /**
     * Returns the strategy name for configuration purposes.
     */
    String getName();

    /**
     * Distributes available slots across eligible campaigns.
     *
     * @param campaigns     list of eligible campaigns (already filtered by business hours)
     * @param totalSlots    total number of slots to distribute
     * @param context       additional context (e.g., remaining calls per campaign)
     * @return map of campaign ID to number of slots allocated
     */
    Map<Campaign, Integer> distribute(List<Campaign> campaigns, int totalSlots, SchedulingContext context);
}
