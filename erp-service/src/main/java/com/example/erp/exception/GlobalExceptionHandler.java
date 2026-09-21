package com.example.erp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ErpProcessingException.class)
    ResponseEntity<ApiError> handleProcessingFailure(ErpProcessingException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "O ERP não conseguiu processar o pedido");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidationFailure(MethodArgumentNotValidException exception) {
        return response(HttpStatus.BAD_REQUEST, "Dados do pedido enviados ao ERP inválidos");
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), message, OffsetDateTime.now()));
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleMalformedRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "Dados do pedido enviados ao ERP inválidos");
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<ApiError> handleConflict(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(
                new ApiError(exception.getStatusCode().value(), "O pedido está em conflito com uma aceitação anterior", OffsetDateTime.now()));
    }
}
