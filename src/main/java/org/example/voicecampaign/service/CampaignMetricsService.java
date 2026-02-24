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

    private static final String ACTIVE_SLOTS_KEY = "campaign:%s:active_slots";
    private static final String METRICS_KEY = "campaign:%s:metrics:%s";
    private static final long METRICS_TTL_HOURS = 24;

    public int getActiveSlots(UUID campaignId) {
        String key = String.format(ACTIVE_SLOTS_KEY, campaignId);
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value) : 0;
    }

    public int incrementActiveSlots(UUID campaignId) {
        String key = String.format(ACTIVE_SLOTS_KEY, campaignId);
        Long newValue = redisTemplate.opsForValue().increment(key);
        return newValue != null ? newValue.intValue() : 1;
    }

    public int decrementActiveSlots(UUID campaignId) {
        String key = String.format(ACTIVE_SLOTS_KEY, campaignId);
        Long newValue = redisTemplate.opsForValue().decrement(key);
        if (newValue != null && newValue < 0) {
            redisTemplate.opsForValue().set(key, "0");
            return 0;
        }
        return newValue != null ? newValue.intValue() : 0;
    }

    public void resetActiveSlots(UUID campaignId) {
        String key = String.format(ACTIVE_SLOTS_KEY, campaignId);
        redisTemplate.opsForValue().set(key, "0");
    }

    public void incrementMetric(UUID campaignId, String metricName) {
        String key = String.format(METRICS_KEY, campaignId, metricName);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, METRICS_TTL_HOURS, TimeUnit.HOURS);
    }

    public long getMetric(UUID campaignId, String metricName) {
        String key = String.format(METRICS_KEY, campaignId, metricName);
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0;
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

    public boolean tryAcquireSlot(UUID campaignId, int concurrencyLimit) {
        int currentSlots = getActiveSlots(campaignId);
        if (currentSlots >= concurrencyLimit) {
            return false;
        }
        incrementActiveSlots(campaignId);
        return true;
    }

    public void releaseSlot(UUID campaignId) {
        decrementActiveSlots(campaignId);
    }
}
