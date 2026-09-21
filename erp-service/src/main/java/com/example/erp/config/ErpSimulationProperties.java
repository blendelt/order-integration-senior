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
            throw new IllegalArgumentException("Intervalo de atraso da simulação do ERP inválido");
        }
        if (!Double.isFinite(randomFailureRate) || randomFailureRate < 0 || randomFailureRate > 1) {
            throw new IllegalArgumentException("A taxa de falhas aleatórias do ERP deve estar entre 0 e 1");
        }
    }
}
