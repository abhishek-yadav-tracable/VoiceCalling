package org.example.voicecampaign.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignCreateRequest {
    
    @NotBlank(message = "Campaign name is required")
    @Size(min = 1, max = 255, message = "Campaign name must be between 1 and 255 characters")
    private String name;
    
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;
    
    @NotEmpty(message = "At least one phone number is required")
    @Size(max = 100000, message = "Cannot exceed 100,000 phone numbers per request")
    private List<String> phoneNumbers;
    
    @Positive(message = "Concurrency limit must be positive")
    @Max(value = 10000, message = "Concurrency limit cannot exceed 10000")
    @Builder.Default
    private Integer concurrencyLimit = 10;
    
    @Min(value = 1, message = "Priority must be at least 1")
    @Max(value = 10, message = "Priority cannot exceed 10")
    @Builder.Default
    private Integer priority = 5;
    
    private RetryConfigDto retryConfig;
    
    private BusinessHoursDto businessHours;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryConfigDto {
        @Min(value = 0, message = "Max retries cannot be negative")
        @Max(value = 10, message = "Max retries cannot exceed 10")
        @Builder.Default
        private Integer maxRetries = 3;
        
        @Min(value = 100, message = "Initial backoff must be at least 100ms")
        @Max(value = 60000, message = "Initial backoff cannot exceed 60 seconds")
        @Builder.Default
        private Long syncInitialBackoffMs = 1000L;
        
        @Min(value = 1, message = "Backoff multiplier must be at least 1")
        @Max(value = 10, message = "Backoff multiplier cannot exceed 10")
        @Builder.Default
        private Double syncBackoffMultiplier = 2.0;
        
        @Min(value = 1000, message = "Callback retry delay must be at least 1 second")
        @Max(value = 3600000, message = "Callback retry delay cannot exceed 1 hour")
        @Builder.Default
        private Long callbackRetryDelayMs = 30000L;
        
        @Min(value = 10000, message = "Callback timeout must be at least 10 seconds")
        @Max(value = 3600000, message = "Callback timeout cannot exceed 1 hour")
        @Builder.Default
        private Long callbackTimeoutMs = 120000L;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessHoursDto {
        @Builder.Default
        private LocalTime startTime = LocalTime.of(9, 0);
        @Builder.Default
        private LocalTime endTime = LocalTime.of(18, 0);
        @Builder.Default
        private String timezone = "UTC";
        @Builder.Default
        private String allowedDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";
    }
}
