package org.example.voicecampaign.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerCallRequest {
    
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
}
