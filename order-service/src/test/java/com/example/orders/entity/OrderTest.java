package com.example.orders.entity;

import com.example.orders.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class OrderTest {
    private Order order() { return new Order("TEST-1", "Test", BigDecimal.TEN); }

    @Test
    void completesOnlyAfterStartingAndCountsAttempt() {
        Order order = order();
        assertThatThrownBy(order::completeProcessing).isInstanceOf(IllegalStateException.class);
        order.startProcessing();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.getAttemptCount()).isEqualTo(1);
        order.completeProcessing();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SUCCESS);
        assertThat(order.getLastError()).isNull();
        assertThatThrownBy(order::startProcessing).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordsBoundedErrorAndDoesNotAutomaticallyRetry() {
        Order order = order();
        order.startProcessing();
        order.failProcessing("x".repeat(1200));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ERROR);
        assertThat(order.getLastError()).hasSize(1000);
        assertThatThrownBy(order::startProcessing).isInstanceOf(IllegalStateException.class);
        assertThat(order.getAttemptCount()).isEqualTo(1);
    }
}
