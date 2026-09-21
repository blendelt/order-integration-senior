package com.example.orders.dto;

public record ProcessingResult(int processed, int succeeded, int failed) {
}
