package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "erp.client")
public record ErpClientProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public ErpClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("A URL base do ERP é obrigatória");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("O tempo limite de conexão com o ERP deve ser positivo");
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("O tempo limite de leitura da resposta do ERP deve ser positivo");
        }
    }
}
