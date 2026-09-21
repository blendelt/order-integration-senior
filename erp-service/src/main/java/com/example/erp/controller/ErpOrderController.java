package com.example.erp.controller;

import com.example.erp.dto.ErpOrderRequest;
import com.example.erp.dto.ErpOrderResponse;
import com.example.erp.service.ErpOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/erp/orders")
public class ErpOrderController {

    private final ErpOrderService erpOrderService;

    public ErpOrderController(ErpOrderService erpOrderService) {
        this.erpOrderService = erpOrderService;
    }

    @PostMapping
    public ResponseEntity<ErpOrderResponse> process(@Valid @RequestBody ErpOrderRequest request) {
        return ResponseEntity.ok(erpOrderService.process(request));
    }
}
