package org.example.voicecampaign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackRequest {
    
    @NotBlank(message = "External call ID is required")
    private String externalCallId;
    
    @NotNull(message = "Status is required")
    private CallbackStatus status;
    
    private Integer durationSeconds;
    
    private String failureReason;
    
    public enum CallbackStatus {
        COMPLETED,
        FAILED,
        NO_ANSWER,
        BUSY,
        REJECTED
    }
}
