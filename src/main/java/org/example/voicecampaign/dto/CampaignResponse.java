package org.example.voicecampaign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.voicecampaign.domain.model.CampaignStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignResponse {
    
    private UUID id;
    private String name;
    private String description;
    private CampaignStatus status;
    private int concurrencyLimit;
    private int priority;
    private RetryConfigDto retryConfig;
    private BusinessHoursDto businessHours;
    private CampaignMetrics metrics;
    private Instant createdAt;
    private Instant updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryConfigDto {
        private int maxRetries;
        private long syncInitialBackoffMs;
        private double syncBackoffMultiplier;
        private long callbackRetryDelayMs;
        private long callbackTimeoutMs;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessHoursDto {
        private LocalTime startTime;
        private LocalTime endTime;
        private String timezone;
        private String allowedDays;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignMetrics {
        private long totalCalls;
        private long pendingCalls;
        private long inProgressCalls;
        private long completedCalls;
        private long failedCalls;
        private long permanentlyFailedCalls;
        private long totalRetries;
    }
}
