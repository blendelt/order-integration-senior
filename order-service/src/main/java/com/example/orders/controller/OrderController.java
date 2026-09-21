package com.example.orders.controller;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.OrderResponse;
import com.example.orders.dto.RetryOrderRequest;
import com.example.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return service.create(request);
    }
    @GetMapping
    public List<OrderResponse> list() { return service.findAll(); }

    @PostMapping("/{id}/retry")
    public OrderResponse retry(@PathVariable Long id, @Valid @RequestBody RetryOrderRequest request) {
        return service.retry(id, request);
    }
}
