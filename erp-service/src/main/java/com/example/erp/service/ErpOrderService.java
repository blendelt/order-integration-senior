package com.example.erp.service;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;

import com.example.erp.config.ErpSimulationProperties;
import com.example.erp.dto.ErpOrderRequest;
import com.example.erp.dto.ErpOrderResponse;
import com.example.erp.exception.ErpProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ErpOrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErpOrderService.class);

    private final ErpSimulationProperties properties;
    // Simulator-only deduplication. Production requires durable storage.
    private final ConcurrentHashMap<String, AcceptedOrder> accepted = new ConcurrentHashMap<>();
    private record AcceptedOrder(ErpOrderRequest request, ErpOrderResponse response) {}

    public ErpOrderService(ErpSimulationProperties properties) {
        this.properties = properties;
    }

    public ErpOrderResponse process(ErpOrderRequest request) {
        return accepted.compute(request.externalId(), (key, previous) -> {
            if (previous != null) {
                if (!previous.request().customerName().equals(request.customerName())
                        || previous.request().totalValue().compareTo(request.totalValue()) != 0) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Identifier already accepted with different data");
                }
                return previous;
            }
            return new AcceptedOrder(request, accept(request));
        }).response();
    }

    private ErpOrderResponse accept(ErpOrderRequest request) {
        simulateDelay();

        if (mustFail(request.externalId())) {
            LOGGER.warn("ERP simulation failed for reference={}", org.springframework.util.DigestUtils.md5DigestAsHex(request.externalId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            throw new ErpProcessingException("ERP could not process order " + request.externalId());
        }

        LOGGER.info("ERP simulation processed reference={}", org.springframework.util.DigestUtils.md5DigestAsHex(request.externalId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return new ErpOrderResponse(request.externalId(), "ACCEPTED", OffsetDateTime.now());
    }

    private void simulateDelay() {
        long delay = properties.minimumDelayMs() == properties.maximumDelayMs()
                ? properties.minimumDelayMs()
                : ThreadLocalRandom.current().nextLong(
                        properties.minimumDelayMs(), properties.maximumDelayMs() + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ErpProcessingException("ERP processing was interrupted");
        }
    }

    private boolean mustFail(String externalId) {
        boolean deterministicFailure = properties.failurePrefix() != null
                && !properties.failurePrefix().isBlank()
                && externalId.startsWith(properties.failurePrefix());
        boolean randomFailure = properties.randomFailureRate() > 0
                && ThreadLocalRandom.current().nextDouble() < properties.randomFailureRate();
        return deterministicFailure || randomFailure;
    }
}
