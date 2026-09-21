package com.example.orders.enums;

public enum ErpFailure {
    TIMEOUT("ERP_TIMEOUT: resposta não confirmada; confira a integração no ERP antes de reenviar"),
    UNAVAILABLE("ERP_UNAVAILABLE: falha na comunicação; resultado não confirmado"),
    HTTP_ERROR("ERP_HTTP_ERROR: o ERP retornou um status de erro"),
    INVALID_RESPONSE("ERP_INVALID_RESPONSE: não foi possível confirmar a aceitação do pedido"),
    UNKNOWN("Falha na integração com o ERP; consulte os logs do serviço");

    private final String safeMessage;

    ErpFailure(String safeMessage) { this.safeMessage = safeMessage; }

    public String safeMessage() { return safeMessage; }
}
