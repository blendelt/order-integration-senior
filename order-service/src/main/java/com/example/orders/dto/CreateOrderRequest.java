package com.example.orders.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank(message = "Informe o identificador externo") @Size(max = 100, message = "O identificador externo deve ter no máximo 100 caracteres") String externalId,
        @NotBlank(message = "Informe o nome do cliente") @Size(max = 200, message = "O nome do cliente deve ter no máximo 200 caracteres") String customerName,
        @NotNull(message = "Informe o valor do pedido") @DecimalMin(value = "0.01", message = "O valor do pedido deve ser de pelo menos 0,01") @Digits(integer = 17, fraction = 2, message = "O valor deve ter no máximo 17 dígitos inteiros e 2 casas decimais") BigDecimal totalValue
) {
}
