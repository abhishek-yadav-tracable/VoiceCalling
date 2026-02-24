package org.example.voicecampaign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResponse {
    private int totalReceived;
    private int totalImported;
    private int duplicatesSkipped;
    private int invalidSkipped;
    private String status;
}
