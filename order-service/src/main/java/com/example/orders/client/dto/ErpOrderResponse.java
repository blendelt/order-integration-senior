package com.example.orders.client.dto;

import java.time.OffsetDateTime;

public record ErpOrderResponse(
        String externalId,
        String status,
        OffsetDateTime processedAt
) {
}
