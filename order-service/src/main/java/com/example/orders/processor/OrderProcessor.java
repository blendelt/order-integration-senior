package com.example.orders.processor;

import com.example.orders.client.ErpClient;
import com.example.orders.config.OrderProcessingProperties;
import com.example.orders.dto.ProcessingResult;
import com.example.orders.entity.Order;
import com.example.orders.exception.ErpClientException;
import com.example.orders.service.OrderProcessingTransactions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Service
public class OrderProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderProcessor.class);
    private final OrderProcessingTransactions transactions;
    private final ErpClient erpClient;
    private final OrderProcessingProperties properties;
    private final Executor executor;

    public OrderProcessor(OrderProcessingTransactions transactions, ErpClient erpClient,
                          OrderProcessingProperties properties, @Qualifier("orderExecutor") Executor executor) {
        this.transactions = transactions;
        this.erpClient = erpClient;
        this.properties = properties;
        this.executor = executor;
    }

    // Não precisa de um transactional porque o HTTP não deve manter uma conexão com o banco.
    public synchronized ProcessingResult processPending() {
        // A fila compartilhada é limitada a um lote liberado por instância, os bloqueios do banco protegem as outras instâncias.
        List<CompletableFuture<ProcessingResult>> tasks = new ArrayList<>();
        RuntimeException submissionFailure = null;
        for (int i = 0; i < properties.batchSize(); i++) {
            try {
                tasks.add(CompletableFuture.supplyAsync(this::processOne, executor));
            } catch (RuntimeException exception) {
                submissionFailure = exception;
                break;
            }
        }
        //  Processa o trabalho aceito, mesmo que o sistema rejeite um envio ou um trabalhador falhe.
        try {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw exception;
        }
        if (submissionFailure != null) {
            throw submissionFailure;
        }
        int succeeded = 0;
        int failed = 0;
        for (CompletableFuture<ProcessingResult> task : tasks) {
            succeeded += task.join().succeeded();
            failed += task.join().failed();
        }
        return new ProcessingResult(succeeded + failed, succeeded, failed);
    }

    private ProcessingResult processOne() {
        Optional<Order> reserved = transactions.reserveNext();
        if (reserved.isEmpty()) {
            return new ProcessingResult(0, 0, 0);
        }
        Order order = reserved.get();
        try {
            erpClient.send(order);
        } catch (ErpClientException exception) {
            // Do not persist response bodies, customer data or arbitrary exception messages.
            transactions.fail(order.getId(), exception.getFailure().safeMessage());
            LOGGER.warn("Falha na integração do pedido id={} tipoErro={}",
                    order.getId(), exception.getFailure().name());
            return new ProcessingResult(1, 0, 1);
        }
        transactions.complete(order.getId());
        LOGGER.info("Integração do pedido concluída id={}", order.getId());
        return new ProcessingResult(1, 1, 0);
    }
}
