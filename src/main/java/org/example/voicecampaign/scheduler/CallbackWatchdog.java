package org.example.voicecampaign.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.CallRequest;
import org.example.voicecampaign.dto.CallbackRequest;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.example.voicecampaign.service.CallService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Monitors for calls that have not received a callback within the expected timeout.
 * 
 * <p>Runs periodically to detect IN_PROGRESS calls whose {@code expectedCallbackBy} timestamp
 * has passed. These calls are treated as failed due to callback timeout and are marked for retry.</p>
 * 
 * <p>This handles scenarios where:</p>
 * <ul>
 *   <li>Telephony provider fails to send a callback</li>
 *   <li>Callback is lost due to network issues</li>
 *   <li>Application restarts while calls are in progress</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CallbackWatchdog {

    private final CallRequestRepository callRequestRepository;
    private final CallService callService;

    @Value("${voice-campaign.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * Scheduled task that checks for and handles timed-out calls.
     */
    @Scheduled(fixedRateString = "${voice-campaign.watchdog.fixed-rate-ms:30000}")
    public void checkForTimedOutCalls() {
        if (!schedulerEnabled) {
            return;
        }

        Instant now = Instant.now();
        List<CallRequest> timedOutCalls = callRequestRepository.findTimedOutCalls(now);

        if (timedOutCalls.isEmpty()) {
            return;
        }

        log.info("Found {} timed out calls", timedOutCalls.size());

        for (CallRequest callRequest : timedOutCalls) {
            try {
                handleTimedOutCall(callRequest);
            } catch (Exception e) {
                log.error("Error handling timed out call {}: {}", callRequest.getId(), e.getMessage());
            }
        }
    }

    private void handleTimedOutCall(CallRequest callRequest) {
        log.warn("Call {} timed out (expected callback by {})", 
                callRequest.getId(), callRequest.getExpectedCallbackBy());

        // Treat timeout as a failure and trigger retry logic
        CallbackRequest syntheticCallback = CallbackRequest.builder()
                .externalCallId(callRequest.getExternalCallId())
                .status(CallbackRequest.CallbackStatus.FAILED)
                .failureReason("Callback timeout - no response from telephony provider")
                .build();

        callService.handleCallback(syntheticCallback);
    }
}
