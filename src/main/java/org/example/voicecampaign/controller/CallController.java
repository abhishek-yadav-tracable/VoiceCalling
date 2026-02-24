package org.example.voicecampaign.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.voicecampaign.dto.CallResponse;
import org.example.voicecampaign.dto.CallbackRequest;
import org.example.voicecampaign.dto.TriggerCallRequest;
import org.example.voicecampaign.service.CallService;
import org.example.voicecampaign.service.CampaignService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;
    private final CampaignService campaignService;

    @PostMapping
    public ResponseEntity<CallResponse> triggerCall(@Valid @RequestBody TriggerCallRequest request) {
        CallResponse response = callService.triggerSingleCall(request.getPhoneNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{callId}")
    public ResponseEntity<CallResponse> getCallStatus(@PathVariable UUID callId) {
        return ResponseEntity.ok(campaignService.getCallStatus(callId));
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(@Valid @RequestBody CallbackRequest request) {
        callService.handleCallback(request);
        return ResponseEntity.ok().build();
    }
}
