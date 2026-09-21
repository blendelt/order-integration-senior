package com.example.orders.processor;

import com.example.orders.client.ErpClient;
import com.example.orders.config.OrderProcessingProperties;
import com.example.orders.dto.ProcessingResult;
import com.example.orders.entity.Order;
import com.example.orders.exception.ErpClientException;
import com.example.orders.service.OrderProcessingTransactions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderProcessorTest {
    private final OrderProcessingTransactions transactions = mock(OrderProcessingTransactions.class);
    private final ErpClient client = mock(ErpClient.class);
    private final OrderProcessor processor = new OrderProcessor(transactions, client, new OrderProcessingProperties(2, 1), Runnable::run);

    private Order order(long id) {
        Order order = new Order("TEST-" + id, "Test", BigDecimal.TEN);
        ReflectionTestUtils.setField(order, "id", id);
        order.startProcessing();
        return order;
    }

    @Test
    void returnsZeroWhenNoPendingOrders() {
        when(transactions.reserveNext()).thenReturn(Optional.empty());
        assertThat(processor.processPending()).isEqualTo(new ProcessingResult(0, 0, 0));
        verifyNoInteractions(client);
    }

    @Test
    void continuesAfterErpFailureAndDoesNotPersistSensitiveMessage() {
        Order first = order(1), second = order(2);
        when(transactions.reserveNext()).thenReturn(Optional.of(first), Optional.of(second));
        when(client.send(first)).thenThrow(new ErpClientException("sensitive payload"));
        assertThat(processor.processPending()).isEqualTo(new ProcessingResult(2, 1, 1));
        verify(transactions).fail(1L, "Falha na integração com o ERP; consulte os logs do serviço");
        verify(transactions).complete(2L);
        verify(transactions, times(2)).reserveNext();
        verify(transactions, never()).complete(1L);
    }

    @Test
    void stopsAtConfiguredBatchSize() {
        when(transactions.reserveNext()).thenReturn(Optional.of(order(1)), Optional.of(order(2)), Optional.of(order(3)));
        assertThat(processor.processPending()).isEqualTo(new ProcessingResult(2, 2, 0));
        verify(transactions, times(2)).reserveNext();
        verify(client, times(2)).send(any());
    }

    @Test
    void doesNotHideProgrammingErrorsAsErpFailures() {
        Order order = order(1);
        when(transactions.reserveNext()).thenReturn(Optional.of(order));
        when(client.send(order)).thenThrow(new IllegalStateException("programming error"));
        assertThatThrownBy(processor::processPending).hasMessage("programming error");
        verify(transactions, never()).fail(any(), any());
    }

    @Test
    void recordsTimeoutAndContinuesNextOrder() {
        Order first = order(1), second = order(2);
        when(transactions.reserveNext()).thenReturn(Optional.of(first), Optional.of(second));
        var failure = com.example.orders.enums.ErpFailure.TIMEOUT;
        when(client.send(first)).thenThrow(new ErpClientException(failure, "private detail", null));
        assertThat(processor.processPending()).isEqualTo(new ProcessingResult(2, 1, 1));
        verify(transactions).fail(1L, failure.safeMessage());
        verify(transactions).complete(2L);
        verify(client, times(1)).send(first);
    }

    @Test
    void doesNotTurnDatabaseCompletionFailureIntoErpFailure() {
        when(transactions.reserveNext()).thenReturn(Optional.of(order(1)));
        doThrow(new IllegalStateException("database unavailable")).when(transactions).complete(1L);
        assertThatThrownBy(processor::processPending).hasMessage("database unavailable");
        verify(transactions, never()).fail(any(), any());
    }
}
