package org.example.voicecampaign.service;

import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.CallRequest;
import org.example.voicecampaign.domain.entity.Campaign;
import org.example.voicecampaign.domain.model.CallStatus;
import org.example.voicecampaign.domain.model.CampaignStatus;
import org.example.voicecampaign.dto.CallResponse;
import org.example.voicecampaign.dto.CallbackRequest;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.example.voicecampaign.repository.CampaignRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.voicecampaign.exception.CallNotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class CallService {

    private final CallRequestRepository callRequestRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignMetricsService metricsService;
    private final TelephonyService telephonyService;

    @Value("${voice-campaign.defaults.callback-timeout-ms:120000}")
    private long defaultCallbackTimeoutMs;

    public CallService(
            CallRequestRepository callRequestRepository,
            CampaignRepository campaignRepository,
            CampaignMetricsService metricsService,
            @Lazy TelephonyService telephonyService) {
        this.callRequestRepository = callRequestRepository;
        this.campaignRepository = campaignRepository;
        this.metricsService = metricsService;
        this.telephonyService = telephonyService;
    }

    @Transactional
    public CallResponse triggerSingleCall(String phoneNumber) {
        // Create ad-hoc campaign for single call
        Campaign campaign = Campaign.builder()
                .name("Single Call - " + phoneNumber)
                .status(CampaignStatus.IN_PROGRESS)
                .concurrencyLimit(1)
                .build();
        campaign = campaignRepository.save(campaign);

        CallRequest callRequest = CallRequest.builder()
                .campaign(campaign)
                .phoneNumber(phoneNumber)
                .status(CallStatus.PENDING)
                .build();
        callRequest = callRequestRepository.save(callRequest);

        // Trigger the call
        executeCall(callRequest);

        return mapToCallResponse(callRequest);
    }

    public void executeCall(CallRequest callRequest) {
        Campaign campaign = callRequest.getCampaign();
        long callbackTimeoutMs = campaign.getRetryConfig() != null 
                ? campaign.getRetryConfig().getCallbackTimeoutMs() 
                : defaultCallbackTimeoutMs;

        String externalCallId = null;
        try {
            externalCallId = telephonyService.initiateCall(
                    callRequest.getPhoneNumber(), 
                    callRequest.getId()
            );

            // Save the external call ID immediately in a separate transaction
            // to ensure it's committed before any callback arrives
            saveCallInProgress(callRequest.getId(), externalCallId, callbackTimeoutMs);

            log.info("Call initiated: {} -> external: {}", callRequest.getId(), externalCallId);

        } catch (Exception e) {
            log.error("Failed to initiate call {}: {}", callRequest.getId(), e.getMessage());
            // If call was initiated but saveCallInProgress failed, we still need to handle it
            // The callback will arrive but we need to release the slot now
            if (externalCallId != null) {
                log.error("Call was initiated (external: {}) but failed to save state - releasing slot", externalCallId);
                metricsService.releaseSlot(campaign.getId());
            } else {
                // Call initiation failed - handle as sync failure (which releases slot)
                handleSyncFailure(callRequest.getId(), campaign.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void saveCallInProgress(UUID callRequestId, String externalCallId, long callbackTimeoutMs) {
        CallRequest callRequest = callRequestRepository.findById(callRequestId)
                .orElseThrow(() -> new CallNotFoundException(callRequestId));
        Instant expectedCallbackBy = Instant.now().plusMillis(callbackTimeoutMs);
        callRequest.markInProgress(externalCallId, expectedCallbackBy);
        callRequestRepository.save(callRequest);
    }

    @Transactional
    public void handleSyncFailure(UUID callRequestId, UUID campaignId, String reason) {
        // Re-fetch entity to ensure it's attached to the current persistence context
        CallRequest callRequest = callRequestRepository.findByIdWithCampaign(callRequestId)
                .orElseThrow(() -> new CallNotFoundException(callRequestId));
        Campaign campaign = callRequest.getCampaign();
        
        int maxRetries = campaign.getRetryConfig() != null 
                ? campaign.getRetryConfig().getMaxRetries() 
                : 3;

        if (callRequest.getRetryCount() < maxRetries) {
            // Calculate exponential backoff for sync failures
            long backoffMs = campaign.getRetryConfig() != null
                    ? campaign.getRetryConfig().calculateSyncBackoff(callRequest.getRetryCount() + 1)
                    : 1000L * (long) Math.pow(2, callRequest.getRetryCount());

            Instant nextRetryAt = Instant.now().plusMillis(backoffMs);
            callRequest.markFailed(reason, nextRetryAt);
            metricsService.incrementMetric(campaign.getId(), "retries");
            log.info("Call {} scheduled for retry at {} (attempt {})", 
                    callRequest.getId(), nextRetryAt, callRequest.getRetryCount());
        } else {
            callRequest.markPermanentlyFailed(reason);
            log.warn("Call {} permanently failed after {} retries", 
                    callRequest.getId(), callRequest.getRetryCount());
        }

        metricsService.releaseSlot(campaignId);
        callRequestRepository.save(callRequest);
    }

    @Transactional
    public void handleCallback(CallbackRequest callback) {
        log.info("Received callback for external call: {} with status: {}", 
                callback.getExternalCallId(), callback.getStatus());

        Optional<CallRequest> optionalCallRequest = callRequestRepository
                .findByExternalCallId(callback.getExternalCallId());

        if (optionalCallRequest.isEmpty()) {
            log.warn("No call request found for external call ID: {}", callback.getExternalCallId());
            // Cannot release slot - we don't know which campaign this belongs to
            // This is a data inconsistency that shouldn't happen in normal operation
            return;
        }

        CallRequest callRequest = optionalCallRequest.get();
        Campaign campaign = callRequest.getCampaign();

        if (callRequest.getStatus() != CallStatus.IN_PROGRESS) {
            log.warn("Callback received for call {} in unexpected status: {} - slot may already be released", 
                    callRequest.getId(), callRequest.getStatus());
            // Don't release slot again - it was already released when status changed
            return;
        }

        try {
            switch (callback.getStatus()) {
                case COMPLETED -> handleCallCompleted(callRequest, callback.getDurationSeconds());
                case FAILED, NO_ANSWER, BUSY, REJECTED -> 
                        handleCallFailed(callRequest, callback.getFailureReason());
            }
        } finally {
            // Always release the slot when processing a callback for an IN_PROGRESS call
            metricsService.releaseSlot(campaign.getId());
        }

        // Check if campaign is complete
        checkCampaignCompletion(campaign);
    }

    private void handleCallCompleted(CallRequest callRequest, Integer durationSeconds) {
        callRequest.markCompleted(durationSeconds);
        callRequestRepository.save(callRequest);
        metricsService.incrementMetric(callRequest.getCampaign().getId(), "completed");
        log.info("Call {} completed with duration {}s", callRequest.getId(), durationSeconds);
    }

    private void handleCallFailed(CallRequest callRequest, String reason) {
        Campaign campaign = callRequest.getCampaign();
        int maxRetries = campaign.getRetryConfig() != null 
                ? campaign.getRetryConfig().getMaxRetries() 
                : 3;

        if (callRequest.getRetryCount() < maxRetries) {
            // Fixed delay for callback-triggered retries
            long retryDelayMs = campaign.getRetryConfig() != null
                    ? campaign.getRetryConfig().getCallbackRetryDelayMs()
                    : 30000L;

            Instant nextRetryAt = Instant.now().plusMillis(retryDelayMs);
            callRequest.markFailed(reason, nextRetryAt);
            metricsService.incrementMetric(campaign.getId(), "retries");
            log.info("Call {} failed, scheduled for retry at {} (attempt {})", 
                    callRequest.getId(), nextRetryAt, callRequest.getRetryCount());
        } else {
            callRequest.markPermanentlyFailed(reason);
            metricsService.incrementMetric(campaign.getId(), "permanently_failed");
            log.warn("Call {} permanently failed after {} retries: {}", 
                    callRequest.getId(), callRequest.getRetryCount(), reason);
        }

        callRequestRepository.save(callRequest);
    }

    @Transactional
    public void checkCampaignCompletion(Campaign campaign) {
        long pendingCount = callRequestRepository.countByCampaignIdAndStatus(
                campaign.getId(), CallStatus.PENDING);
        long failedCount = callRequestRepository.countByCampaignIdAndStatus(
                campaign.getId(), CallStatus.FAILED);
        long inProgressCount = callRequestRepository.countByCampaignIdAndStatus(
                campaign.getId(), CallStatus.IN_PROGRESS);

        if (pendingCount == 0 && failedCount == 0 && inProgressCount == 0) {
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaignRepository.save(campaign);
            log.info("Campaign {} completed", campaign.getId());
        }
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
}
