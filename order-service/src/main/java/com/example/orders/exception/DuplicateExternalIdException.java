package com.example.orders.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateExternalIdException extends RuntimeException {
    public DuplicateExternalIdException(String externalId) {
        super("Já existe um pedido com esse externalId");
    }
}
