package org.example.voicecampaign.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.domain.entity.CallRequest;
import org.example.voicecampaign.domain.entity.Campaign;
import org.example.voicecampaign.dto.CallbackRequest;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.example.voicecampaign.service.CallService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallbackWatchdog {

    private final CallRequestRepository callRequestRepository;
    private final CallService callService;

    @Value("${voice-campaign.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Scheduled(fixedRate = 30000) // Run every 30 seconds
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
