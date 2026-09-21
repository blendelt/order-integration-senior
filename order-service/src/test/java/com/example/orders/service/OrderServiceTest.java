package com.example.orders.service;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.exception.DuplicateExternalIdException;
import com.example.orders.repository.OrderRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderService service = new OrderService(repository);
    private final CreateOrderRequest request = new CreateOrderRequest("TEST", "Test", BigDecimal.TEN);

    @Test
    void mapsConcurrentUniqueViolationToConflict() {
        var cause = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"),
                "uk_orders_external_id");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", cause));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(DuplicateExternalIdException.class);
    }

    @Test
    void preservesOtherDatabaseFailures() {
        var failure = new DataIntegrityViolationException("other constraint");
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.create(request)).isSameAs(failure);
    }
}
