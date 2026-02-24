package org.example.voicecampaign.scheduler.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for selecting the active scheduling strategy based on configuration.
 */
@Component
@Slf4j
public class SchedulingStrategyFactory {

    private final Map<String, SchedulingStrategy> strategies;
    private final String defaultStrategyName;

    public SchedulingStrategyFactory(
            List<SchedulingStrategy> strategyList,
            @Value("${voice-campaign.scheduler.strategy:round-robin}") String defaultStrategyName) {
        
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(SchedulingStrategy::getName, Function.identity()));
        this.defaultStrategyName = defaultStrategyName;
        
        log.info("Loaded {} scheduling strategies: {}", strategies.size(), strategies.keySet());
        log.info("Default scheduling strategy: {}", defaultStrategyName);
    }

    /**
     * Returns the configured scheduling strategy.
     */
    public SchedulingStrategy getStrategy() {
        return getStrategy(defaultStrategyName);
    }

    /**
     * Returns a specific scheduling strategy by name.
     * Falls back to round-robin if not found.
     */
    public SchedulingStrategy getStrategy(String name) {
        SchedulingStrategy strategy = strategies.get(name);
        if (strategy == null) {
            log.warn("Scheduling strategy '{}' not found, falling back to round-robin", name);
            strategy = strategies.get("round-robin");
        }
        return strategy;
    }

    /**
     * Returns all available strategy names.
     */
    public List<String> getAvailableStrategies() {
        return List.copyOf(strategies.keySet());
    }
}
