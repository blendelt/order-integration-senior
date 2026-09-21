package com.example.erp.dto;

import java.time.OffsetDateTime;

public record ErpOrderResponse(
        String externalId,
        String status,
        OffsetDateTime processedAt
) {
}
