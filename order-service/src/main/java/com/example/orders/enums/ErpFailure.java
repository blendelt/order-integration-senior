package com.example.orders.enums;

public enum ErpFailure {
    TIMEOUT("ERP_TIMEOUT: response not confirmed; reconcile before retry"),
    UNAVAILABLE("ERP_UNAVAILABLE: communication failed; result not confirmed"),
    HTTP_ERROR("ERP_HTTP_ERROR: ERP returned an error status"),
    INVALID_RESPONSE("ERP_INVALID_RESPONSE: acceptance could not be confirmed"),
    UNKNOWN("ERP integration failed; check service logs");

    private final String safeMessage;

    ErpFailure(String safeMessage) { this.safeMessage = safeMessage; }

    public String safeMessage() { return safeMessage; }
}
