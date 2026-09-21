package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orders.processing")
public record OrderProcessingProperties(int batchSize, int workers) {
    public OrderProcessingProperties {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("O tamanho do lote de processamento deve estar entre 1 e 100");
        }
        if (workers < 1 || workers > 16) {
            throw new IllegalArgumentException("A quantidade de workers deve estar entre 1 e 16");
        }
    }
}
