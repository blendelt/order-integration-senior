package com.example.orders.processor;

import com.example.orders.dto.ProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orders.processing.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class OrderProcessingJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderProcessingJob.class);
    private final OrderProcessor processor;

    public OrderProcessingJob(OrderProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${orders.processing.fixed-delay:60000}",
            initialDelayString = "${orders.processing.initial-delay:60000}")
    public void processPending() {
        ProcessingResult result = processor.processPending();
        if (result.processed() > 0) {
            LOGGER.info("Scheduled processing finished processed={} succeeded={} failed={}",
                    result.processed(), result.succeeded(), result.failed());
        }
    }
}
