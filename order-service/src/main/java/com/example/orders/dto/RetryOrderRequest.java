package com.example.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record RetryOrderRequest(
        @NotNull @Valid CreateOrderRequest order,
        @NotNull Long version,
        @AssertTrue(message = "Confirme que o pedido não foi integrado no ERP") boolean confirmedNotIntegrated
) {}
