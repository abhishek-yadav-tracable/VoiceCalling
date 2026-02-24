package org.example.voicecampaign.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportRequest {
    
    @NotEmpty(message = "Phone numbers list cannot be empty")
    @Size(max = 100000, message = "Cannot exceed 100,000 phone numbers per batch")
    private List<String> phoneNumbers;
    
    @Min(value = 100, message = "Batch size must be at least 100")
    @Max(value = 10000, message = "Batch size cannot exceed 10,000")
    @Builder.Default
    private int batchSize = 1000;
}
