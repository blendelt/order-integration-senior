package com.example.orders.controller;

import com.example.orders.dto.ProcessingResult;
import com.example.orders.processor.OrderProcessor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders/process")
public class OrderProcessingController {
    private final OrderProcessor processor;
    public OrderProcessingController(OrderProcessor processor) { this.processor = processor; }
    @PostMapping
    public ProcessingResult process() { return processor.processPending(); }
}
