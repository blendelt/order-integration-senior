package com.example.orders.client;

import com.example.orders.client.dto.ErpOrderResponse;
import com.example.orders.entity.Order;
import com.example.orders.exception.ErpClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpErpClientTest {

    private MockRestServiceServer server;
    private HttpErpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://erp-service:8081");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpErpClient(builder.build());
    }

    @Test
    void sendsOrderAndReturnsAcceptedResponse() {
        server.expect(once(), requestTo("http://erp-service:8081/erp/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "externalId": "ERP-12345",
                          "customerName": "Empresa ABC",
                          "totalValue": 1500.00
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "externalId": "ERP-12345",
                          "status": "ACCEPTED",
                          "processedAt": "2026-09-16T01:00:00-03:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        ErpOrderResponse response = client.send(order("ERP-12345"));

        assertThat(response.externalId()).isEqualTo("ERP-12345");
        assertThat(response.status()).isEqualTo("ACCEPTED");
        server.verify();
    }

    @Test
    void convertsErpHttpFailureToClientException() {
        server.expect(once(), requestTo("http://erp-service:8081/erp/orders"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "status": 500,
                                  "message": "ERP processing failed"
                                }
                                """));

        assertThatThrownBy(() -> client.send(order("FAIL-12345")))
                .isInstanceOf(ErpClientException.class)
                .hasMessage("ERP retornou o status HTTP 500");
        server.verify();
    }

    @Test
    void rejectsResponseWithDifferentExternalId() {
        server.expect(once(), requestTo("http://erp-service:8081/erp/orders"))
                .andRespond(withSuccess("""
                        {
                          "externalId": "OTHER-ID",
                          "status": "ACCEPTED",
                          "processedAt": "2026-09-16T01:00:00-03:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.send(order("ERP-12345")))
                .isInstanceOf(ErpClientException.class)
                .hasMessage("ERP retornou um identificador externo diferente do enviado");
        server.verify();
    }

    @Test
    void rejectsEmptyResponse() {
        server.expect(once(), requestTo("http://erp-service:8081/erp/orders"))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> client.send(order("ERP-12345")))
                .isInstanceOf(ErpClientException.class)
                .hasMessage("ERP retornou uma resposta vazia");
        server.verify();
    }

    private Order order(String externalId) {
        return new Order(externalId, "Empresa ABC", new BigDecimal("1500.00"));
    }

    @Test
    void classifiesConnectionFailureWithoutLeakingItsMessage() {
        server.expect(requestTo("http://erp-service:8081/erp/orders"))
                .andRespond(request -> { throw new java.net.ConnectException("private connection detail"); });
        assertThatThrownBy(() -> client.send(order("ERP-12345")))
                .isInstanceOfSatisfying(ErpClientException.class, error -> {
                    assertThat(error.getFailure()).isEqualTo(com.example.orders.enums.ErpFailure.UNAVAILABLE);
                    assertThat(error.getFailure().safeMessage()).doesNotContain("private connection detail");
                });
        server.verify();
    }

    @Test
    void wrapsMalformedJsonInClientException() {
        server.expect(requestTo("http://erp-service:8081/erp/orders"))
                .andRespond(withSuccess("{invalid", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.send(order("ERP-12345")))
                .isInstanceOf(ErpClientException.class)
                .hasMessage("ERP retornou uma resposta que não pôde ser interpretada");
        server.verify();
    }

    @Test
    void rejectsUnacceptedStatus() {
        server.expect(requestTo("http://erp-service:8081/erp/orders"))
                .andRespond(withSuccess("{\"externalId\":\"ERP-12345\",\"status\":\"REJECTED\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.send(order("ERP-12345")))
                .isInstanceOf(ErpClientException.class).hasMessage("ERP não aceitou o pedido");
        server.verify();
    }
}
