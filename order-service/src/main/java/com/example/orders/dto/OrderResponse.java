package com.example.orders.dto;

import com.example.orders.entity.Order;
import com.example.orders.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderResponse(
        Long id,
        String externalId,
        String customerName,
        BigDecimal totalValue,
        OrderStatus status,
        int attemptCount,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getExternalId(),
                order.getCustomerName(),
                order.getTotalValue(),
                order.getStatus(),
                order.getAttemptCount(),
                order.getLastError(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
