package com.example.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.simulation")
public record ErpSimulationProperties(
        long minimumDelayMs,
        long maximumDelayMs,
        String failurePrefix,
        double randomFailureRate
) {
    public ErpSimulationProperties {
        if (minimumDelayMs < 0 || maximumDelayMs < minimumDelayMs || maximumDelayMs == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid ERP simulation delay range");
        }
        if (!Double.isFinite(randomFailureRate) || randomFailureRate < 0 || randomFailureRate > 1) {
            throw new IllegalArgumentException("ERP random failure rate must be between 0 and 1");
        }
    }
}
