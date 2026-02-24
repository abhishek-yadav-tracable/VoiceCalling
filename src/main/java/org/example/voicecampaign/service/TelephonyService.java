package org.example.voicecampaign.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voicecampaign.dto.CallbackRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TelephonyService {

    private final CallService callService;
    
    // Dedicated thread pool for mock callbacks to avoid ForkJoinPool saturation
    private final ScheduledExecutorService callbackExecutor = Executors.newScheduledThreadPool(50);
    
    @Value("${voice-campaign.telephony.mock-enabled:true}")
    private boolean mockEnabled;
    
    public TelephonyService(CallService callService) {
        this.callService = callService;
    }
    
    @Value("${voice-campaign.telephony.mock-min-duration-ms:5000}")
    private long mockMinDurationMs;
    
    @Value("${voice-campaign.telephony.mock-max-duration-ms:15000}")
    private long mockMaxDurationMs;
    
    @Value("${voice-campaign.telephony.mock-callback-failure-rate:0.05}")
    private double mockCallbackFailureRate;

    @Value("${voice-campaign.telephony.mock-no-callback-rate:0.01}")
    private double mockNoCallbackRate;

    @Value("${voice-campaign.telephony.mock-sync-failure-rate:0.005}")
    private double mockSyncFailureRate;

    private final Random random = new Random();

    @CircuitBreaker(name = "telephonyService", fallbackMethod = "initiateCallFallback")
    @RateLimiter(name = "telephonyService")
    public String initiateCall(String phoneNumber, UUID callRequestId) {
        log.info("Initiating call to {} for request {}", phoneNumber, callRequestId);
        
        if (mockEnabled) {
            return initiateMockCall(phoneNumber, callRequestId);
        }
        
        // Real telephony integration would go here
        throw new UnsupportedOperationException("Real telephony not implemented");
    }

    private String initiateMockCall(String phoneNumber, UUID callRequestId) {
        // Simulate rare sync failures (1 in 200 = 0.5%)
        if (random.nextDouble() < mockSyncFailureRate) {
            throw new RuntimeException("Mock sync failure: Network timeout");
        }
        
        String externalCallId = "mock-" + UUID.randomUUID().toString();
        log.debug("Mock call initiated: {} -> {}", callRequestId, externalCallId);
        
        // Schedule async callback using dedicated executor to avoid ForkJoinPool saturation
        long duration = mockMinDurationMs + (long) (random.nextDouble() * (mockMaxDurationMs - mockMinDurationMs));
        log.debug("Mock call {} will complete in {}ms", externalCallId, duration);
        
        // Use scheduled executor with delay instead of Thread.sleep
        callbackExecutor.schedule(() -> {
            try {
                double outcome = random.nextDouble();
                
                // 1% chance: No callback at all (simulates timeout - watchdog will handle)
                if (outcome < mockNoCallbackRate) {
                    log.debug("Mock call {} - simulating no callback (timeout scenario)", externalCallId);
                    return; // Don't send callback, let watchdog handle timeout
                }
                
                // 5% chance: Failed callback
                CallbackRequest callback;
                if (outcome < mockNoCallbackRate + mockCallbackFailureRate) {
                    CallbackRequest.CallbackStatus failureStatus = getRandomFailureStatus();
                    callback = CallbackRequest.builder()
                            .externalCallId(externalCallId)
                            .status(failureStatus)
                            .failureReason("Mock failure: " + failureStatus)
                            .build();
                    log.debug("Mock call {} failed with status {}", externalCallId, failureStatus);
                } else {
                    // 94% chance: Success
                    int callDuration = 10 + random.nextInt(180);
                    callback = CallbackRequest.builder()
                            .externalCallId(externalCallId)
                            .status(CallbackRequest.CallbackStatus.COMPLETED)
                            .durationSeconds(callDuration)
                            .build();
                    log.debug("Mock call {} completed with duration {}s", externalCallId, callDuration);
                }
                
                callService.handleCallback(callback);
                
            } catch (Exception e) {
                log.error("Error in mock callback for {}: {}", externalCallId, e.getMessage());
            }
        }, duration + 1000, TimeUnit.MILLISECONDS);
        
        return externalCallId;
    }

    private CallbackRequest.CallbackStatus getRandomFailureStatus() {
        CallbackRequest.CallbackStatus[] failures = {
                CallbackRequest.CallbackStatus.FAILED,
                CallbackRequest.CallbackStatus.NO_ANSWER,
                CallbackRequest.CallbackStatus.BUSY,
                CallbackRequest.CallbackStatus.REJECTED
        };
        return failures[random.nextInt(failures.length)];
    }

    public String initiateCallFallback(String phoneNumber, UUID callRequestId, Throwable t) {
        log.warn("Circuit breaker fallback for call to {}: {}", phoneNumber, t.getMessage());
        throw new RuntimeException("Telephony service unavailable: " + t.getMessage(), t);
    }
}
