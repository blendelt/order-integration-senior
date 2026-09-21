package com.example.orders.entity;

import com.example.orders.enums.OrderStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_external_id", columnNames = "external_id")
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "total_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @jakarta.persistence.Version
    private long version;

    public long getVersion() { return version; }

    protected Order() {
    }

    public Order(String externalId, String customerName, BigDecimal totalValue) {
        this.externalId = externalId;
        this.customerName = customerName;
        this.totalValue = totalValue;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void startProcessing() {
        requireStatus(OrderStatus.PENDING);
        status = OrderStatus.PROCESSING;
        attemptCount++;
        lastError = null;
    }

    public void completeProcessing() {
        requireStatus(OrderStatus.PROCESSING);
        status = OrderStatus.SUCCESS;
        lastError = null;
    }

    public void failProcessing(String message) {
        requireStatus(OrderStatus.PROCESSING);
        status = OrderStatus.ERROR;
        String safeMessage = message == null ? "Falha na integração com o ERP" : message;
        lastError = safeMessage.substring(0, Math.min(safeMessage.length(), 1000));
    }

    private void requireStatus(OrderStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Status esperado para o pedido: " + expected + "; status atual: " + status);
        }
    }

    public void reviseAndRetry(String externalId, String customerName, BigDecimal totalValue) {
        requireStatus(OrderStatus.ERROR);
        this.externalId = externalId;
        this.customerName = customerName;
        this.totalValue = totalValue;
        status = OrderStatus.PENDING;
        // Preserve history; the next claim increments attempts and clears the previous error.
    }

    public Long getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getTotalValue() { return totalValue; }
    public OrderStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

}
