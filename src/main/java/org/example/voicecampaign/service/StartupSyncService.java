package org.example.voicecampaign.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.Campaign;
import org.example.voicecampaign.domain.model.CallStatus;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.example.voicecampaign.repository.CampaignRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.example.voicecampaign.domain.model.CampaignStatus;

import java.util.List;
import java.util.Set;

/**
 * Synchronizes Redis state with PostgreSQL on application startup.
 * This ensures resumability after restarts by:
 * 1. Clearing stale Redis queue entries
 * 2. Syncing active slot counts with actual DB IN_PROGRESS counts
 * 3. Resetting queued counts to 0 (scheduler will re-enqueue)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StartupSyncService {

    private final CampaignRepository campaignRepository;
    private final CallRequestRepository callRequestRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String CALL_QUEUE_KEY = "call:queue";
    private static final String ACTIVE_SLOTS_KEY = "campaign:%s:active_slots";
    private static final String QUEUED_KEY = "campaign:%s:queued";

    @PostConstruct
    public void syncOnStartup() {
        log.info("Starting Redis-PostgreSQL synchronization...");
        
        try {
            // 1. Clear the call queue (stale entries from previous run)
            Long queueSize = redisTemplate.opsForList().size(CALL_QUEUE_KEY);
            if (queueSize != null && queueSize > 0) {
                redisTemplate.delete(CALL_QUEUE_KEY);
                log.info("Cleared {} stale entries from call queue", queueSize);
            }

            // 2. Clear all queued counts (will be re-populated by scheduler)
            Set<String> queuedKeys = redisTemplate.keys("campaign:*:queued");
            if (queuedKeys != null && !queuedKeys.isEmpty()) {
                redisTemplate.delete(queuedKeys);
                log.info("Cleared {} queued count keys", queuedKeys.size());
            }

            // 3. Sync active slots with actual DB IN_PROGRESS counts
            List<Campaign> activeCampaigns = campaignRepository.findActiveCampaigns(
                    List.of(CampaignStatus.IN_PROGRESS, CampaignStatus.PENDING, CampaignStatus.PAUSED));
            int syncedCampaigns = 0;
            
            for (Campaign campaign : activeCampaigns) {
                long inProgressCount = callRequestRepository.countByCampaignIdAndStatus(
                        campaign.getId(), CallStatus.IN_PROGRESS);
                
                String key = String.format(ACTIVE_SLOTS_KEY, campaign.getId());
                redisTemplate.opsForValue().set(key, String.valueOf(inProgressCount));
                syncedCampaigns++;
            }

            // 4. Clear slots for inactive campaigns
            Set<String> allSlotKeys = redisTemplate.keys("campaign:*:active_slots");
            if (allSlotKeys != null) {
                Set<String> activeCampaignKeys = new java.util.HashSet<>();
                for (Campaign c : activeCampaigns) {
                    activeCampaignKeys.add(String.format(ACTIVE_SLOTS_KEY, c.getId()));
                }
                
                for (String key : allSlotKeys) {
                    if (!activeCampaignKeys.contains(key)) {
                        redisTemplate.delete(key);
                    }
                }
            }

            log.info("Startup sync complete: synced {} active campaigns", syncedCampaigns);
            
        } catch (Exception e) {
            log.error("Error during startup sync: {}", e.getMessage(), e);
            // Don't fail startup - system can still work, just might have stale data
        }
    }
}
