package com.example.erp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "erp.simulation.minimum-delay-ms=0",
        "erp.simulation.maximum-delay-ms=0",
        "erp.simulation.failure-prefix=FAIL-",
        "erp.simulation.random-failure-rate=0"
})
@AutoConfigureMockMvc
class ErpOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsSuccessForRegularOrder() throws Exception {
        mockMvc.perform(post("/erp/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalId": "ERP-12345",
                                  "customerName": "Empresa ABC",
                                  "totalValue": 1500.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("ERP-12345"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void returnsInternalServerErrorForDeterministicFailure() throws Exception {
        mockMvc.perform(post("/erp/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalId": "FAIL-12345",
                                  "customerName": "Empresa ABC",
                                  "totalValue": 1500.00
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
