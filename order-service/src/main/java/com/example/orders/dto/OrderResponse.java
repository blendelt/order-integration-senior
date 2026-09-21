package com.example.orders.dto;

import com.example.orders.entity.Order;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderResponse(Long id, String externalId, String customerName,
                            BigDecimal totalValue, OffsetDateTime createdAt) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getExternalId(), order.getCustomerName(),
                order.getTotalValue(), order.getCreatedAt());
    }
}
