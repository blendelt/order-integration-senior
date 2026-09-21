package com.example.orders.repository;

import com.example.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    boolean existsByExternalId(String externalId);
    java.util.List<Order> findTop20ByStatusOrderByCreatedAtAscIdAsc(com.example.orders.enums.OrderStatus status);
}
