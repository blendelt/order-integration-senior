package com.example.orders.exception;

import com.example.orders.enums.ErpFailure;

public class ErpClientException extends RuntimeException {
    private final ErpFailure failure;

    public ErpClientException(String message) {
        this(ErpFailure.UNKNOWN, message, null);
    }

    public ErpClientException(String message, Throwable cause) {
        this(ErpFailure.UNKNOWN, message, cause);
    }

    public ErpClientException(ErpFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public ErpFailure getFailure() { return failure; }
}
