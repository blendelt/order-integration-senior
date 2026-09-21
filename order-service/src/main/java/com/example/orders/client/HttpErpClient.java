package com.example.orders.client;

import com.example.orders.client.dto.ErpOrderRequest;
import com.example.orders.client.dto.ErpOrderResponse;
import com.example.orders.entity.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpErpClient implements ErpClient {
    private final RestClient erpRestClient;
    public HttpErpClient(RestClient erpRestClient) { this.erpRestClient = erpRestClient; }

    @Override
    public ErpOrderResponse send(Order order) {
        var response = erpRestClient.post().uri("/erp/orders")
                .body(new ErpOrderRequest(order.getExternalId(), order.getCustomerName(), order.getTotalValue()))
                .retrieve().body(ErpOrderResponse.class);
        if (response == null || !order.getExternalId().equals(response.externalId())
                || !"ACCEPTED".equals(response.status())) {
            throw new org.springframework.web.client.RestClientException("Invalid ERP response");
        }
        return response;
    }
}
