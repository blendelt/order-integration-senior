package com.example.orders.service;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.RetryOrderRequest;
import com.example.orders.entity.Order;
import com.example.orders.enums.OrderStatus;
import com.example.orders.exception.DuplicateExternalIdException;
import com.example.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderRetryTest {
    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderService service = new OrderService(repository);
    private final CreateOrderRequest data = new CreateOrderRequest("CORRECTED", "Cliente", BigDecimal.TEN);

    private Order failed() {
        Order order = new Order("FAIL-1", "Original", BigDecimal.ONE);
        order.startProcessing();
        order.failProcessing("ERP_HTTP_ERROR");
        when(repository.lockForRetry(1L)).thenReturn(Optional.of(order));
        return order;
    }

    @Test void retriesWithCorrectedDataAndPreservesAttempts() {
        Order order = failed();
        service.retry(1L, new RetryOrderRequest(data, 0L, true));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getExternalId()).isEqualTo("CORRECTED");
        assertThat(order.getCustomerName()).isEqualTo("Cliente");
        assertThat(order.getTotalValue()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(order.getAttemptCount()).isEqualTo(1);
        assertThat(order.getLastError()).isEqualTo("ERP_HTTP_ERROR");
        verify(repository).flush();
        assertThatThrownBy(() -> service.retry(1L, new RetryOrderRequest(data, 0L, true)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejectsStaleEditAndMissingConfirmation() {
        Order order = failed();
        assertThatThrownBy(() -> service.retry(1L, new RetryOrderRequest(data, 1L, true)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.retry(1L, new RetryOrderRequest(data, 0L, false)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ERROR);
        verify(repository, never()).flush();
    }

    @Test void rejectsDuplicateIdentifierWithoutChangingOrder() {
        Order order = failed();
        when(repository.existsByExternalIdAndIdNot("CORRECTED", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.retry(1L, new RetryOrderRequest(data, 0L, true)))
                .isInstanceOf(DuplicateExternalIdException.class);
        assertThat(order.getExternalId()).isEqualTo("FAIL-1");
        verify(repository, never()).flush();
    }

    @Test void rejectsUnknownOrder() {
        when(repository.lockForRetry(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.retry(1L, new RetryOrderRequest(data, 0L, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test void rejectsAllNonErrorStates() {
        for (OrderStatus status : new OrderStatus[] {OrderStatus.PENDING, OrderStatus.PROCESSING, OrderStatus.SUCCESS}) {
            Order order = new Order("ORIGINAL", "Cliente", BigDecimal.ONE);
            org.springframework.test.util.ReflectionTestUtils.setField(order, "status", status);
            when(repository.lockForRetry(1L)).thenReturn(Optional.of(order));
            assertThatThrownBy(() -> service.retry(1L, new RetryOrderRequest(data, 0L, true)))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
            assertThat(order.getExternalId()).isEqualTo("ORIGINAL");
        }
        verify(repository, never()).flush();
    }

    @Test void translatesDuplicateCreatedBetweenCheckAndFlush() {
        failed();
        var cause = new org.hibernate.exception.ConstraintViolationException("duplicate",
                new java.sql.SQLException("duplicate", "23505"), "uk_orders_external_id");
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate", cause))
                .when(repository).flush();
        assertThatThrownBy(() -> service.retry(1L, new RetryOrderRequest(data, 0L, true)))
                .isInstanceOf(DuplicateExternalIdException.class);
    }
}
