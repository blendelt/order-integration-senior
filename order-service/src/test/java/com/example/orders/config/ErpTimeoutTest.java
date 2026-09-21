package com.example.orders.config;

import com.example.orders.client.HttpErpClient;
import com.example.orders.entity.Order;
import com.example.orders.enums.ErpFailure;
import com.example.orders.exception.ErpClientException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class  ErpTimeoutTest {
    @Test
    void actualHttpReadTimeoutDoesNotRetry() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newSingleThreadExecutor();
        var received = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var requests = new AtomicInteger();
        server.setExecutor(executor);
        server.createContext("/erp/orders", exchange -> {
            requests.incrementAndGet();
            received.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
        try {
            var properties = new ErpClientProperties("http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2), Duration.ofMillis(500));
            var client = new HttpErpClient(new ErpClientConfig().erpRestClient(properties));
            long start = System.nanoTime();
            assertThatThrownBy(() -> client.send(new Order("TIMEOUT-1", "Test", BigDecimal.TEN)))
                    .isInstanceOfSatisfying(ErpClientException.class,
                            error -> assertThat(error.getFailure()).isEqualTo(ErpFailure.TIMEOUT));
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
            assertThat(received.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(requests.get()).isEqualTo(1);
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
