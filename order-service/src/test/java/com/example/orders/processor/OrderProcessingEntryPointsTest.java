package com.example.orders.processor;

import com.example.orders.controller.OrderProcessingController;
import com.example.orders.dto.ProcessingResult;
import com.example.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderProcessingEntryPointsTest {
    @Test
    void manualEndpointReturnsFinishedBatchSummary() throws Exception {
        OrderProcessor processor = mock(OrderProcessor.class);
        when(processor.processPending()).thenReturn(new ProcessingResult(2, 1, 1));
        MockMvcBuilders.standaloneSetup(new OrderProcessingController(processor))
                .build().perform(post("/orders/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1));
        verify(processor).processPending();
    }

    @Test
    void scheduledJobUsesSameProcessor() {
        OrderProcessor processor = mock(OrderProcessor.class);
        when(processor.processPending()).thenReturn(new ProcessingResult(0, 0, 0));
        new OrderProcessingJob(processor).processPending();
        verify(processor).processPending();
    }
}
