package com.example.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record RetryOrderRequest(
        @NotNull(message = "Informe os dados do pedido") @Valid CreateOrderRequest order,
        @NotNull(message = "Informe a versão do pedido") Long version,
        @AssertTrue(message = "Confirme que o pedido não foi integrado no ERP") boolean confirmedNotIntegrated
) {}
