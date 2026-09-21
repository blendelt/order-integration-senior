package com.example.orders.controller;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.OrderResponse;
import com.example.orders.dto.RetryOrderRequest;
import com.example.orders.entity.Order;
import com.example.orders.exception.GlobalExceptionHandler;
import com.example.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderRetryContractTest {
    private final OrderService service = mock(OrderService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final String payload = """
            {"order":{"externalId":"FIXED","customerName":"Cliente","totalValue":25.50},
             "version":3,"confirmedNotIntegrated":true}
            """;

    @Test void acceptsFrontendPayloadAndReturnsCurrentVersion() throws Exception {
        var order = new Order("FIXED", "Cliente", new BigDecimal("25.50"));
        ReflectionTestUtils.setField(order, "id", 7L);
        ReflectionTestUtils.setField(order, "version", 4L);
        when(service.retry(eq(7L), any())).thenReturn(OrderResponse.from(order));
        mvc.perform(post("/orders/7/retry").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").value(7)).andExpect(jsonPath("$.version").value(4));
        verify(service).retry(7L, new RetryOrderRequest(
                new CreateOrderRequest("FIXED", "Cliente", new BigDecimal("25.50")), 3L, true));
    }

    @Test void listIncludesVersionRequiredForEditing() throws Exception {
        var order = new Order("FIXED", "Cliente", BigDecimal.TEN);
        ReflectionTestUtils.setField(order, "version", 3L);
        when(service.findAll()).thenReturn(List.of(OrderResponse.from(order)));
        mvc.perform(get("/orders")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(3));
    }

    @Test void rejectsMissingVersionConfirmationAndInvalidEditedFields() throws Exception {
        for (String invalid : List.of(payload.replace("\"version\":3", "\"version\":null"),
                payload.replace("true", "false"), payload.replace("FIXED", ""))) {
            mvc.perform(post("/orders/7/retry").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test void returns404And409WithoutInternalDetails() throws Exception {
        when(service.retry(eq(7L), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "private"));
        mvc.perform(post("/orders/7/retry").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        when(service.retry(eq(7L), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "private"));
        mvc.perform(post("/orders/7/retry").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_CONFLICT"));
    }
}
