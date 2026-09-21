package com.example.orders.processor;

import com.example.orders.client.ErpClient;
import com.example.orders.config.OrderProcessingProperties;
import com.example.orders.entity.Order;
import com.example.orders.enums.OrderStatus;
import com.example.orders.repository.OrderRepository;
import com.example.orders.service.OrderProcessingTransactions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"orders.processing.scheduling-enabled=false", "orders.processing.workers=4"})
@org.junit.jupiter.api.Tag("postgres")
@Testcontainers
class OrderPostgresConcurrencyTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired OrderRepository repository;
    @Autowired com.example.orders.service.OrderService orderService;
    @Autowired OrderProcessingTransactions transactions;
    @Autowired @Qualifier("orderExecutor") Executor executor;
    @MockitoBean ErpClient client;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearIsolatedDatabase() {
        repository.deleteAllInBatch();
    }

    @Test
    void concurrentRetryAcceptsOnlyOneEditAndKeepsHistory() throws Exception {
        Order original = new Order("FAIL-RETRY", "Original", BigDecimal.ONE);
        original.startProcessing();
        original.failProcessing("ERP failure");
        Long id = repository.saveAndFlush(original).getId();
        Order saved = repository.findById(id).orElseThrow();
        var request = new com.example.orders.dto.RetryOrderRequest(
                new com.example.orders.dto.CreateOrderRequest("RETRY-FIXED", "Corrected", BigDecimal.TEN),
                saved.getVersion(), true);
        var callers = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> retry = () -> {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
            try {
                orderService.retry(saved.getId(), request);
                return true;
            } catch (org.springframework.web.server.ResponseStatusException conflict) {
                assertThat(conflict.getStatusCode().value()).isEqualTo(409);
                return false;
            }
        };
        try {
            var first = callers.submit(retry);
            var second = callers.submit(retry);
            start.countDown();
            boolean a = first.get(10, TimeUnit.SECONDS);
            boolean b = second.get(10, TimeUnit.SECONDS);
            assertThat(a ^ b).isTrue();
            Order current = repository.findById(saved.getId()).orElseThrow();
            assertThat(current.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(current.getExternalId()).isEqualTo("RETRY-FIXED");
            assertThat(current.getAttemptCount()).isEqualTo(1);
            assertThat(current.getLastError()).isEqualTo("ERP failure");
            assertThat(current.getCreatedAt()).isEqualTo(saved.getCreatedAt());
            assertThat(current.getVersion()).isGreaterThan(saved.getVersion());
        } finally {
            start.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void concurrentCreatesPersistOnlyOneExternalId() throws Exception {
        var callers = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> create = () -> {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
            try {
                repository.saveAndFlush(new Order("SAME-ID", "Test", BigDecimal.TEN));
                return true;
            } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
                return false;
            }
        };
        try {
            var first = callers.submit(create);
            var second = callers.submit(create);
            start.countDown();
            boolean a = first.get(10, TimeUnit.SECONDS);
            boolean b = second.get(10, TimeUnit.SECONDS);
            assertThat(a ^ b).isTrue();
            assertThat(repository.count()).isEqualTo(1);
        } finally {
            start.countDown();
            callers.shutdownNow();
        }
    }
    @Test
    void skipsLockedOrderAndMakesItAvailableAgainAfterRollback() throws Exception {
        Order original = repository.saveAndFlush(new Order("LOCK-1", "Test", BigDecimal.TEN));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var callers = Executors.newFixedThreadPool(2);
        try {
            var holder = callers.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                Order order = repository.lockNextPending().orElseThrow();
                order.startProcessing();
                repository.flush();
                locked.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                status.setRollbackOnly();
                return order.getId();
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            // Must finish while the first transaction STILL holds the row lock.
            var contender = callers.submit(transactions::reserveNext);
            assertThat(contender.get(5, TimeUnit.SECONDS)).isEmpty();
            release.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).isEqualTo(original.getId());
            Order afterRollback = repository.findById(original.getId()).orElseThrow();
            assertThat(afterRollback.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(afterRollback.getAttemptCount()).isZero();
            Order reserved = transactions.reserveNext().orElseThrow();
            assertThat(reserved.getId()).isEqualTo(original.getId());
            assertThat(repository.findById(original.getId()).orElseThrow().getAttemptCount()).isEqualTo(1);
            assertThat(transactions.reserveNext()).isEmpty();
        } finally {
            release.countDown();
            callers.shutdown();
            if (!callers.awaitTermination(20, TimeUnit.SECONDS)) callers.shutdownNow();
        }
    }

    @Test
    void independentCoordinatorsDoNotSendTheSameOrderTwice() throws Exception {
        for (int i = 0; i < 8; i++) repository.saveAndFlush(new Order("PG-" + i, "Test", BigDecimal.TEN));
        var sends = new ConcurrentHashMap<String, AtomicInteger>();
        var inFlight = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        when(client.send(any())).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            // Reading via another transaction verifies reservation was committed before HTTP.
            assertThat(repository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PROCESSING);
            sends.computeIfAbsent(order.getExternalId(), key -> new AtomicInteger()).incrementAndGet();
            inFlight.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
            return null;
        });
        var properties = new OrderProcessingProperties(8, 4);
        // Separate monitors model independent instances; PostgreSQL must arbitrate reservations.
        var first = new OrderProcessor(transactions, client, properties, executor);
        var second = new OrderProcessor(transactions, client, properties, executor);
        var callers = Executors.newFixedThreadPool(2);
        try {
            var a = callers.submit(first::processPending);
            var b = callers.submit(second::processPending);
            assertThat(inFlight.await(15, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(a.get(30, TimeUnit.SECONDS).processed() + b.get(30, TimeUnit.SECONDS).processed()).isEqualTo(8);
            assertThat(sends).hasSize(8);
            sends.values().forEach(count -> assertThat(count.get()).isEqualTo(1));
            assertThat(repository.findAll()).allSatisfy(order -> {
                assertThat(order.getStatus()).isEqualTo(OrderStatus.SUCCESS);
                assertThat(order.getAttemptCount()).isEqualTo(1);
            });
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }
}
