package org.example.voicecampaign.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.dto.*;
import org.example.voicecampaign.service.CampaignService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(@Valid @RequestBody CampaignCreateRequest request) {
        CampaignResponse response = campaignService.createCampaign(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getAllCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(campaignService.getAllCampaigns(page, size));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> getCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.getCampaign(campaignId));
    }

    @PostMapping("/{campaignId}/start")
    public ResponseEntity<CampaignResponse> startCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.startCampaign(campaignId));
    }

    @PostMapping("/{campaignId}/pause")
    public ResponseEntity<CampaignResponse> pauseCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.pauseCampaign(campaignId));
    }

    @PostMapping("/{campaignId}/cancel")
    public ResponseEntity<CampaignResponse> cancelCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.cancelCampaign(campaignId));
    }

    @GetMapping("/{campaignId}/calls")
    public ResponseEntity<List<CallResponse>> getCampaignCalls(
            @PathVariable UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(campaignService.getCampaignCalls(campaignId, page, size, status));
    }

    @PostMapping(value = "/{campaignId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchImportResponse> importPhoneNumbers(
            @PathVariable UUID campaignId,
            @RequestParam("file") MultipartFile file) {
        
        log.info("Importing phone numbers for campaign {} from file: {}", campaignId, file.getOriginalFilename());
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            List<String> phoneNumbers = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toList());
            
            BatchImportResponse response = campaignService.importPhoneNumbers(campaignId, phoneNumbers);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to import phone numbers", e);
            return ResponseEntity.badRequest().body(
                    BatchImportResponse.builder()
                            .status("FAILED: " + e.getMessage())
                            .build()
            );
        }
    }

    @PostMapping("/{campaignId}/import/batch")
    public ResponseEntity<BatchImportResponse> importPhoneNumbersBatch(
            @PathVariable UUID campaignId,
            @Valid @RequestBody BatchImportRequest request) {
        
        log.info("Batch importing {} phone numbers for campaign {}", 
                request.getPhoneNumbers().size(), campaignId);
        
        BatchImportResponse response = campaignService.importPhoneNumbers(campaignId, request.getPhoneNumbers());
        return ResponseEntity.ok(response);
    }
}
