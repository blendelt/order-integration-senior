package com.example.erp.exception;

import java.time.OffsetDateTime;

public record ApiError(
        int status,
        String message,
        OffsetDateTime timestamp
) {
}
