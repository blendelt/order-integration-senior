package com.example.orders.client.dto;

import java.math.BigDecimal;

public record ErpOrderRequest(
        String externalId,
        String customerName,
        BigDecimal totalValue
) {
}
