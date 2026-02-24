package org.example.voicecampaign.controller;

import lombok.RequiredArgsConstructor;
import org.example.voicecampaign.dto.GlobalMetricsResponse;
import org.example.voicecampaign.service.GlobalMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final GlobalMetricsService globalMetricsService;

    @GetMapping
    public ResponseEntity<GlobalMetricsResponse> getGlobalMetrics() {
        return ResponseEntity.ok(globalMetricsService.getGlobalMetrics());
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetMetrics() {
        globalMetricsService.resetMetricsStartTime();
        return ResponseEntity.ok().build();
    }
}
