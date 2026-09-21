package com.example.orders.processor;

import com.example.orders.client.ErpClient;
import com.example.orders.dto.ProcessingResult;
import com.example.orders.enums.OrderStatus;
import com.example.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class OrderProcessor {
    private final OrderRepository repository;
    private final ErpClient erpClient;
    public OrderProcessor(OrderRepository repository, ErpClient erpClient) {
        this.repository = repository;
        this.erpClient = erpClient;
    }

    // Sequential prototype: synchronized only protects this application instance.
    public synchronized ProcessingResult processPending() {
        int succeeded = 0, failed = 0;
        for (var order : repository.findTop20ByStatusOrderByCreatedAtAscIdAsc(OrderStatus.PENDING)) {
            order.startProcessing();
            order = repository.saveAndFlush(order);
            try {
                erpClient.send(order);
            } catch (RestClientException exception) {
                order.failProcessing("ERP integration failed");
                repository.saveAndFlush(order);
                failed++;
                continue;
            }
            order.completeProcessing();
            repository.saveAndFlush(order);
            succeeded++;
        }
        return new ProcessingResult(succeeded + failed, succeeded, failed);
    }
}
