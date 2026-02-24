package org.example.voicecampaign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalMetricsResponse {
    private int totalCampaigns;
    private int activeCampaigns;
    private int completedCampaigns;
    
    private long totalCalls;
    private long pendingCalls;
    private long inProgressCalls;
    private long completedCalls;
    private long failedCalls;
    private long permanentlyFailedCalls;
    
    private long totalRetries;
    
    // Worker thread pool utilization
    private int workerPoolSize;
    private int activeWorkerThreads;
    private double workerThreadUtilizationPercent;
    private long queueDepth;
    
    // Concurrency slot utilization (calls in progress vs total allowed)
    private int totalConcurrencySlots;
    private int activeConcurrencySlots;
    private double concurrencyUtilizationPercent;
    
    // Throughput
    private double callsPerSecond;
    private double avgCallDurationSeconds;
}
