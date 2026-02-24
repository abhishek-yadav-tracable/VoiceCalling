package org.example.voicecampaign.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.model.CallStatus;
import org.example.voicecampaign.dto.CampaignResponse.CampaignMetrics;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignMetricsService {

    private final StringRedisTemplate redisTemplate;
    private final CallRequestRepository callRequestRepository;

    @org.springframework.beans.factory.annotation.Value("${voice-campaign.metrics.ttl-hours:24}")
    private long metricsTtlHours;

    private static final String ACTIVE_SLOTS_KEY = "campaign:%s:active_slots";
    private static final String METRICS_KEY = "campaign:%s:metrics:%s";

    public int getActiveSlots(UUID campaignId) {
        String key = String.format(ACTIVE_SLOTS_KEY, campaignId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid active slots value for campaign {}: {}", campaignId, value);
            return 0;
        }
    }

    public void incrementActiveSlots(UUID campaignId) {
        String key = String.format(ACTIVE_SLOTS_KEY, campaignId);
        redisTemplate.opsForValue().increment(key);
    }

    public void decrementActiveSlots(UUID campaignId) {
        String key = String.format(ACTIVE_SLOTS_KEY, campaignId);
        Long newValue = redisTemplate.opsForValue().decrement(key);
        if (newValue != null && newValue < 0) {
            redisTemplate.opsForValue().set(key, "0");
        }
    }

    public void resetActiveSlots(UUID campaignId) {
        String key = String.format(ACTIVE_SLOTS_KEY, campaignId);
        redisTemplate.opsForValue().set(key, "0");
    }

    public void incrementMetric(UUID campaignId, String metricName) {
        String key = String.format(METRICS_KEY, campaignId, metricName);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, metricsTtlHours, TimeUnit.HOURS);
    }

    public long getMetric(UUID campaignId, String metricName) {
        String key = String.format(METRICS_KEY, campaignId, metricName);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid metric value for campaign {} metric {}: {}", campaignId, metricName, value);
            return 0;
        }
    }

    public CampaignMetrics getCampaignMetrics(UUID campaignId) {
        List<Object[]> statusCounts = callRequestRepository.countByStatusForCampaign(campaignId);
        
        long totalCalls = 0;
        long pendingCalls = 0;
        long inProgressCalls = 0;
        long completedCalls = 0;
        long failedCalls = 0;
        long permanentlyFailedCalls = 0;
        
        for (Object[] row : statusCounts) {
            CallStatus status = (CallStatus) row[0];
            long count = (Long) row[1];
            totalCalls += count;
            
            switch (status) {
                case PENDING, SCHEDULED -> pendingCalls += count;
                case IN_PROGRESS -> inProgressCalls = count;
                case COMPLETED -> completedCalls = count;
                case FAILED -> failedCalls = count;
                case PERMANENTLY_FAILED -> permanentlyFailedCalls = count;
                default -> {}
            }
        }
        
        long totalRetries = getMetric(campaignId, "retries");
        
        return CampaignMetrics.builder()
                .totalCalls(totalCalls)
                .pendingCalls(pendingCalls)
                .inProgressCalls(inProgressCalls)
                .completedCalls(completedCalls)
                .failedCalls(failedCalls)
                .permanentlyFailedCalls(permanentlyFailedCalls)
                .totalRetries(totalRetries)
                .build();
    }

    public void releaseSlot(UUID campaignId) {
        decrementActiveSlots(campaignId);
    }
}
