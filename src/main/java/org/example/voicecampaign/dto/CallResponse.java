package org.example.voicecampaign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.voicecampaign.domain.model.CallStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallResponse {
    
    private UUID id;
    private UUID campaignId;
    private String phoneNumber;
    private CallStatus status;
    private int retryCount;
    private Instant lastAttemptedAt;
    private String externalCallId;
    private String failureReason;
    private Integer callDurationSeconds;
    private Instant createdAt;
    private Instant updatedAt;
}
