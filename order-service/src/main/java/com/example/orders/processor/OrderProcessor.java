package com.example.orders.processor;

import com.example.orders.client.ErpClient;
import com.example.orders.dto.ProcessingResult;
import com.example.orders.service.OrderProcessingTransactions;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class OrderProcessor {
    private final OrderProcessingTransactions transactions;
    private final ErpClient erpClient;
    public OrderProcessor(OrderProcessingTransactions transactions, ErpClient erpClient) {
        this.transactions = transactions;
        this.erpClient = erpClient;
    }

    // No encompassing transaction: HTTP never holds the claim's connection/lock.
    public synchronized ProcessingResult processPending() {
        int succeeded = 0, failed = 0;
        for (int i = 0; i < 20; i++) {
            var reserved = transactions.reserveNext();
            if (reserved.isEmpty()) break;
            var order = reserved.get();
            try {
                erpClient.send(order);
            } catch (RestClientException exception) {
                transactions.fail(order.getId(), "ERP integration failed");
                failed++;
                continue;
            }
            transactions.complete(order.getId());
            succeeded++;
        }
        return new ProcessingResult(succeeded + failed, succeeded, failed);
    }
}
