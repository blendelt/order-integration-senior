package com.example.orders.exception;

import com.example.orders.controller.OrderController;
import com.example.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HttpErrorContractTest {
    @Test void malformedJsonReturnsSafe400() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(mock(OrderService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{secret-invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("message").value("JSON ou parâmetro inválido"));
    }
    @Test void duplicateReturns409WithoutEchoingPayload() throws Exception {
        var service = mock(OrderService.class);
        when(service.create(any())).thenThrow(new DuplicateExternalIdException("private-data"));
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"externalId":"private-data","customerName":"Test","totalValue":10}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("DUPLICATE_EXTERNAL_ID"))
                .andExpect(jsonPath("message").value("Já existe um pedido com esse externalId"));
    }
    @Test void validationIdentifiesField() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(mock(OrderService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"externalId":"","customerName":"Test","totalValue":10}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("fields.externalId").exists());
    }
}
