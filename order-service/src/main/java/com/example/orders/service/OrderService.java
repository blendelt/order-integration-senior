package com.example.orders.service;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.OrderResponse;
import com.example.orders.entity.Order;
import com.example.orders.exception.DuplicateExternalIdException;
import com.example.orders.repository.OrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        if (orderRepository.existsByExternalId(request.externalId())) {
            throw new DuplicateExternalIdException(request.externalId());
        }

        try {
            Order order = new Order(request.externalId(), request.customerName(), request.totalValue());
            Order saved = orderRepository.saveAndFlush(order);
            LOGGER.info("Order creation prepared id={}", saved.getId());
            return OrderResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && "uk_orders_external_id".equals(violation.getConstraintName())) {
                    throw new DuplicateExternalIdException(request.externalId());
                }
                cause = cause.getCause();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

}
