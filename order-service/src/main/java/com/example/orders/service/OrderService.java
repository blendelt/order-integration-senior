package com.example.orders.service;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.OrderResponse;
import com.example.orders.entity.Order;
import com.example.orders.repository.OrderRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    public OrderService(OrderRepository orderRepository) { this.orderRepository = orderRepository; }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Order order = new Order(request.externalId(), request.customerName(), request.totalValue());
        return OrderResponse.from(orderRepository.saveAndFlush(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(OrderResponse::from).toList();
    }
}
