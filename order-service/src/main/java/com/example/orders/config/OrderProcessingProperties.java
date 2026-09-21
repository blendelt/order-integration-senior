package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orders.processing")
public record OrderProcessingProperties(int batchSize, int workers) {
    public OrderProcessingProperties {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Processing batch size must be between 1 and 100");
        }
        if (workers < 1 || workers > 16) {
            throw new IllegalArgumentException("Processing workers must be between 1 and 16");
        }
    }
}
