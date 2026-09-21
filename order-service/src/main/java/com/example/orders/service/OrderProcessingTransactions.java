package com.example.orders.service;

import com.example.orders.entity.Order;
import com.example.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OrderProcessingTransactions {

    private final OrderRepository repository;

    public OrderProcessingTransactions(OrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<Order> reserveNext() {
        Optional<Order> order = repository.lockNextPending();
        order.ifPresent(Order::startProcessing);
        return order;
    }

    @Transactional
    public void complete(Long id) {
        findOrder(id).completeProcessing();
    }

    @Transactional
    public void fail(Long id, String message) {
        findOrder(id).failProcessing(message);
    }

    private Order findOrder(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Pedido em processamento não encontrado: " + id));
    }
}
