package com.example.orders.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;
    @Column(name = "total_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalValue;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Order() {}
    public Order(String externalId, String customerName, BigDecimal totalValue) {
        this.externalId = externalId;
        this.customerName = customerName;
        this.totalValue = totalValue;
    }
    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(); }
    public Long getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getTotalValue() { return totalValue; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
