package org.example.voicecampaign.exception;

import java.util.UUID;

public class CallNotFoundException extends RuntimeException {
    
    public CallNotFoundException(UUID callId) {
        super("Call not found: " + callId);
    }
}
