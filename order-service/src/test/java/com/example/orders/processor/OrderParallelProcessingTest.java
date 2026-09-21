package com.example.orders.processor;

import com.example.orders.client.ErpClient;
import com.example.orders.config.OrderExecutorConfig;
import com.example.orders.config.OrderProcessingProperties;
import com.example.orders.dto.ProcessingResult;
import com.example.orders.entity.Order;
import com.example.orders.service.OrderProcessingTransactions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderParallelProcessingTest {
    @Test
    void sharesWorkerLimitBetweenOverlappingTriggers() throws Exception {
        var properties = new OrderProcessingProperties(6, 2);
        var executor = new OrderExecutorConfig().orderExecutor(properties);
        executor.initialize();
        var callers = Executors.newFixedThreadPool(2);
        var transactions = mock(OrderProcessingTransactions.class);
        var client = mock(ErpClient.class);
        var ids = new AtomicInteger();
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        when(transactions.reserveNext()).thenAnswer(invocation -> {
            int id = ids.incrementAndGet();
            if (id > 6) return Optional.empty();
            Order order = new Order("PAR-" + id, "Test", BigDecimal.TEN);
            ReflectionTestUtils.setField(order, "id", (long) id);
            order.startProcessing();
            return Optional.of(order);
        });
        when(client.send(any())).thenAnswer(invocation -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            started.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
                return null;
            } finally { active.decrementAndGet(); }
        });
        var processor = new OrderProcessor(transactions, client, properties, executor);
        try {
            Future<ProcessingResult> first = callers.submit(processor::processPending);
            Future<ProcessingResult> second = callers.submit(processor::processPending);
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(active.get()).isEqualTo(2);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).processed() + second.get(10, TimeUnit.SECONDS).processed())
                    .isEqualTo(6);
            assertThat(maximum.get()).isEqualTo(2);
            verify(client, times(6)).send(any());
            for (long id = 1; id <= 6; id++) verify(transactions).complete(id);
        } finally {
            release.countDown();
            callers.shutdownNow();
            executor.destroy();
        }
    }
}
