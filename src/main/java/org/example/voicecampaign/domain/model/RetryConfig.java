package org.example.voicecampaign.domain.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryConfig {
    
    @Builder.Default
    private int maxRetries = 3;
    
    @Builder.Default
    private long syncInitialBackoffMs = 1000;
    
    @Builder.Default
    private double syncBackoffMultiplier = 2.0;
    
    @Builder.Default
    private long callbackRetryDelayMs = 30000;
    
    @Builder.Default
    private long callbackTimeoutMs = 120000;
    
    public long calculateSyncBackoff(int attemptNumber) {
        return (long) (syncInitialBackoffMs * Math.pow(syncBackoffMultiplier, attemptNumber - 1));
    }
}
