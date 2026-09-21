package com.example.orders.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateExternalIdException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleDuplicateExternalId(DuplicateExternalIdException exception) {
        return new ApiError("DUPLICATE_EXTERNAL_ID", exception.getMessage(), OffsetDateTime.now(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return new ApiError("VALIDATION_ERROR", "Dados do pedido inválidos", OffsetDateTime.now(), fields);
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleMalformedRequest(Exception exception) {
        return new ApiError("INVALID_REQUEST", "JSON ou parâmetro inválido", OffsetDateTime.now(), Map.of());
    }

}
