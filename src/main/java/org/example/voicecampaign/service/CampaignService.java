package org.example.voicecampaign.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.CallRequest;
import org.example.voicecampaign.domain.entity.Campaign;
import org.example.voicecampaign.domain.model.BusinessHours;
import org.example.voicecampaign.domain.model.CallStatus;
import org.example.voicecampaign.domain.model.CampaignStatus;
import org.example.voicecampaign.domain.model.RetryConfig;
import org.example.voicecampaign.dto.BatchImportResponse;
import org.example.voicecampaign.dto.CampaignCreateRequest;
import org.example.voicecampaign.dto.CampaignResponse;
import org.example.voicecampaign.dto.CallResponse;
import org.example.voicecampaign.exception.CallNotFoundException;
import org.example.voicecampaign.exception.CampaignNotFoundException;
import org.example.voicecampaign.exception.InvalidOperationException;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.example.voicecampaign.repository.CampaignRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CallRequestRepository callRequestRepository;
    private final CampaignMetricsService metricsService;

    @org.springframework.beans.factory.annotation.Value("${voice-campaign.import.batch-size:1000}")
    private int batchSize;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{6,14}$");

    @Transactional
    public CampaignResponse createCampaign(CampaignCreateRequest request) {
        // Sanitize inputs
        String sanitizedName = sanitizeInput(request.getName());
        String sanitizedDescription = sanitizeInput(request.getDescription());
        
        log.info("Creating campaign: {}", sanitizedName);

        Campaign campaign = Campaign.builder()
                .name(sanitizedName)
                .description(sanitizedDescription)
                .concurrencyLimit(request.getConcurrencyLimit() != null ? request.getConcurrencyLimit() : 10)
                .priority(request.getPriority() != null ? request.getPriority() : 5)
                .retryConfig(mapRetryConfig(request.getRetryConfig()))
                .businessHours(mapBusinessHours(request.getBusinessHours()))
                .status(CampaignStatus.PENDING)
                .build();

        campaign = campaignRepository.save(campaign);

        // Validate and batch insert phone numbers
        List<CallRequest> callRequests = new ArrayList<>();
        Set<String> seenPhones = new HashSet<>();
        int invalidCount = 0;
        int duplicateCount = 0;
        
        for (String phoneNumber : request.getPhoneNumbers()) {
            if (phoneNumber == null) {
                invalidCount++;
                continue;
            }
            
            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            
            // Skip empty/whitespace-only entries
            if (normalizedPhone.isEmpty()) {
                invalidCount++;
                continue;
            }
            
            // Validate phone number format
            if (isInvalidPhoneNumber(normalizedPhone)) {
                log.debug("Invalid phone number skipped: {}", normalizedPhone);
                invalidCount++;
                continue;
            }
            
            // Skip duplicates within this request
            if (seenPhones.contains(normalizedPhone)) {
                duplicateCount++;
                continue;
            }
            seenPhones.add(normalizedPhone);
            
            CallRequest callRequest = CallRequest.builder()
                    .campaign(campaign)
                    .phoneNumber(normalizedPhone)
                    .status(CallStatus.PENDING)
                    .build();
            callRequests.add(callRequest);

            if (callRequests.size() >= batchSize) {
                callRequestRepository.saveAll(callRequests);
                callRequests.clear();
            }
        }

        if (!callRequests.isEmpty()) {
            callRequestRepository.saveAll(callRequests);
        }

        log.info("Created campaign {} with {} valid phone numbers (skipped {} invalid, {} duplicates)", 
                campaign.getId(), seenPhones.size(), invalidCount, duplicateCount);

        return mapToResponse(campaign);
    }
    
    private String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        // Remove potential XSS/injection characters, trim whitespace
        return input.trim()
                .replaceAll("[<>\"'&;]", "")  // Remove HTML/script injection chars
                .replaceAll("\\s+", " ");      // Normalize whitespace
    }
    
    private String normalizePhoneNumber(String phone) {
        // Remove all non-digit characters except leading +
        String normalized = phone.trim();
        if (normalized.startsWith("+")) {
            return "+" + normalized.substring(1).replaceAll("[^0-9]", "");
        }
        return normalized.replaceAll("[^0-9]", "");
    }

    @Transactional(readOnly = true)
    public CampaignResponse getCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));
        return mapToResponse(campaign);
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> getAllCampaigns() {
        return getAllCampaigns(0, 100);
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> getAllCampaigns(int page, int size) {
        int maxSize = Math.min(size, 100);
        return campaignRepository.findAll(PageRequest.of(page, maxSize)).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public CampaignResponse startCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));

        if (campaign.getStatus() != CampaignStatus.PENDING && campaign.getStatus() != CampaignStatus.PAUSED) {
            throw new InvalidOperationException("Campaign cannot be started from status: " + campaign.getStatus());
        }

        campaign.setStatus(CampaignStatus.IN_PROGRESS);
        metricsService.resetActiveSlots(campaignId);
        campaign = campaignRepository.save(campaign);

        log.info("Started campaign: {}", campaignId);
        return mapToResponse(campaign);
    }

    @Transactional
    public CampaignResponse pauseCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));

        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new InvalidOperationException("Campaign cannot be paused from status: " + campaign.getStatus());
        }

        campaign.setStatus(CampaignStatus.PAUSED);
        campaign = campaignRepository.save(campaign);

        log.info("Paused campaign: {}", campaignId);
        return mapToResponse(campaign);
    }

    @Transactional
    public CampaignResponse cancelCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));

        if (campaign.getStatus() == CampaignStatus.COMPLETED || campaign.getStatus() == CampaignStatus.CANCELLED) {
            throw new InvalidOperationException("Campaign cannot be cancelled from status: " + campaign.getStatus());
        }

        campaign.setStatus(CampaignStatus.CANCELLED);
        
        // Cancel all pending calls
        callRequestRepository.bulkUpdateStatus(
                campaignId,
                List.of(CallStatus.PENDING, CallStatus.SCHEDULED, CallStatus.FAILED),
                CallStatus.CANCELLED,
                Instant.now()
        );

        campaign = campaignRepository.save(campaign);

        log.info("Cancelled campaign: {}", campaignId);
        return mapToResponse(campaign);
    }

    @Transactional(readOnly = true)
    public List<CallResponse> getCampaignCalls(UUID campaignId) {
        return getCampaignCalls(campaignId, 0, 50, null);
    }

    @Transactional(readOnly = true)
    public List<CallResponse> getCampaignCalls(UUID campaignId, int page, int size, String status) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new CampaignNotFoundException(campaignId);
        }

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        List<CallRequest> calls;
        
        if (status != null && !status.isEmpty() && !status.equals("ALL")) {
            try {
                CallStatus callStatus = CallStatus.valueOf(status);
                calls = callRequestRepository.findByCampaignIdAndStatus(campaignId, callStatus, pageRequest);
            } catch (IllegalArgumentException e) {
                calls = callRequestRepository.findByCampaignIdPaginated(campaignId, pageRequest);
            }
        } else {
            calls = callRequestRepository.findByCampaignIdPaginated(campaignId, pageRequest);
        }

        return calls.stream()
                .map(this::mapToCallResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CallResponse getCallStatus(UUID callId) {
        CallRequest callRequest = callRequestRepository.findById(callId)
                .orElseThrow(() -> new CallNotFoundException(callId));
        return mapToCallResponse(callRequest);
    }

    private RetryConfig mapRetryConfig(CampaignCreateRequest.RetryConfigDto dto) {
        if (dto == null) {
            return new RetryConfig();
        }
        return RetryConfig.builder()
                .maxRetries(dto.getMaxRetries() != null ? dto.getMaxRetries() : 3)
                .syncInitialBackoffMs(dto.getSyncInitialBackoffMs() != null ? dto.getSyncInitialBackoffMs() : 1000L)
                .syncBackoffMultiplier(dto.getSyncBackoffMultiplier() != null ? dto.getSyncBackoffMultiplier() : 2.0)
                .callbackRetryDelayMs(dto.getCallbackRetryDelayMs() != null ? dto.getCallbackRetryDelayMs() : 30000L)
                .callbackTimeoutMs(dto.getCallbackTimeoutMs() != null ? dto.getCallbackTimeoutMs() : 120000L)
                .build();
    }

    private BusinessHours mapBusinessHours(CampaignCreateRequest.BusinessHoursDto dto) {
        if (dto == null) {
            // Default to 24/7 calling when no business hours specified
            return BusinessHours.allDay();
        }
        return BusinessHours.builder()
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .timezone(dto.getTimezone())
                .allowedDays(dto.getAllowedDays())
                .build();
    }

    private CampaignResponse mapToResponse(Campaign campaign) {
        return CampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .description(campaign.getDescription())
                .status(campaign.getStatus())
                .concurrencyLimit(campaign.getConcurrencyLimit())
                .priority(campaign.getPriority())
                .retryConfig(CampaignResponse.RetryConfigDto.builder()
                        .maxRetries(campaign.getRetryConfig().getMaxRetries())
                        .syncInitialBackoffMs(campaign.getRetryConfig().getSyncInitialBackoffMs())
                        .syncBackoffMultiplier(campaign.getRetryConfig().getSyncBackoffMultiplier())
                        .callbackRetryDelayMs(campaign.getRetryConfig().getCallbackRetryDelayMs())
                        .callbackTimeoutMs(campaign.getRetryConfig().getCallbackTimeoutMs())
                        .build())
                .businessHours(CampaignResponse.BusinessHoursDto.builder()
                        .startTime(campaign.getBusinessHours().getStartTime())
                        .endTime(campaign.getBusinessHours().getEndTime())
                        .timezone(campaign.getBusinessHours().getTimezone())
                        .allowedDays(campaign.getBusinessHours().getAllowedDays())
                        .build())
                .metrics(metricsService.getCampaignMetrics(campaign.getId()))
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }

    private CallResponse mapToCallResponse(CallRequest callRequest) {
        return CallResponse.builder()
                .id(callRequest.getId())
                .campaignId(callRequest.getCampaign().getId())
                .phoneNumber(callRequest.getPhoneNumber())
                .status(callRequest.getStatus())
                .retryCount(callRequest.getRetryCount())
                .lastAttemptedAt(callRequest.getLastAttemptedAt())
                .externalCallId(callRequest.getExternalCallId())
                .failureReason(callRequest.getFailureReason())
                .callDurationSeconds(callRequest.getCallDurationSeconds())
                .createdAt(callRequest.getCreatedAt())
                .updatedAt(callRequest.getUpdatedAt())
                .build();
    }

    @Transactional
    public BatchImportResponse importPhoneNumbers(UUID campaignId, List<String> phoneNumbers) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));

        if (campaign.getStatus() == CampaignStatus.COMPLETED || campaign.getStatus() == CampaignStatus.CANCELLED) {
            throw new InvalidOperationException("Cannot import to campaign with status: " + campaign.getStatus());
        }

        // Track phones seen in this batch to avoid duplicates within the request
        Set<String> seenInBatch = new HashSet<>();

        int totalReceived = phoneNumbers.size();
        int duplicatesSkipped = 0;
        int invalidSkipped = 0;
        List<CallRequest> callRequests = new ArrayList<>();

        for (String phone : phoneNumbers) {
            String normalizedPhone = normalizePhoneNumber(phone);
            
            // Skip empty/whitespace-only entries
            if (normalizedPhone.isEmpty()) {
                invalidSkipped++;
                continue;
            }
            
            // Validate phone number format
            if (isInvalidPhoneNumber(normalizedPhone)) {
                invalidSkipped++;
                continue;
            }

            // Check for duplicates within this batch
            if (seenInBatch.contains(normalizedPhone)) {
                duplicatesSkipped++;
                continue;
            }
            
            // Check for duplicates in database (efficient single-row check)
            if (callRequestRepository.existsByCampaignIdAndPhoneNumber(campaignId, normalizedPhone)) {
                duplicatesSkipped++;
                continue;
            }

            seenInBatch.add(normalizedPhone);
            
            CallRequest callRequest = CallRequest.builder()
                    .campaign(campaign)
                    .phoneNumber(normalizedPhone)
                    .status(CallStatus.PENDING)
                    .build();
            callRequests.add(callRequest);

            // Batch save
            if (callRequests.size() >= batchSize) {
                callRequestRepository.saveAll(callRequests);
                callRequests.clear();
                log.debug("Saved batch of {} phone numbers for campaign {}", batchSize, campaignId);
            }
        }

        // Save remaining
        if (!callRequests.isEmpty()) {
            callRequestRepository.saveAll(callRequests);
        }

        int totalImported = totalReceived - duplicatesSkipped - invalidSkipped;
        log.info("Imported {} phone numbers for campaign {} (duplicates: {}, invalid: {})",
                totalImported, campaignId, duplicatesSkipped, invalidSkipped);

        return BatchImportResponse.builder()
                .totalReceived(totalReceived)
                .totalImported(totalImported)
                .duplicatesSkipped(duplicatesSkipped)
                .invalidSkipped(invalidSkipped)
                .status("SUCCESS")
                .build();
    }

    private boolean isInvalidPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return true;
        }
        return !PHONE_PATTERN.matcher(phone).matches();
    }
}
