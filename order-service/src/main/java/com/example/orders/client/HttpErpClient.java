package com.example.orders.client;

import com.example.orders.client.dto.ErpOrderRequest;
import com.example.orders.client.dto.ErpOrderResponse;
import com.example.orders.entity.Order;
import com.example.orders.enums.ErpFailure;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import com.example.orders.exception.ErpClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

@Component
public class HttpErpClient implements ErpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpErpClient.class);

    private final RestClient erpRestClient;

    public HttpErpClient(RestClient erpRestClient) {
        this.erpRestClient = erpRestClient;
    }

    @Override
    public ErpOrderResponse send(Order order) {
        ErpOrderRequest request = new ErpOrderRequest(
                order.getExternalId(),
                order.getCustomerName(),
                order.getTotalValue()
        );

        try {
            ErpOrderResponse response = erpRestClient.post()
                    .uri("/erp/orders")
                    .body(request)
                    .retrieve()
                    .body(ErpOrderResponse.class);

            validateResponse(order, response);
            LOGGER.info("Aceitação do ERP recebida para o pedido id={}", order.getId());
            return response;
        } catch (RestClientResponseException exception) {
            LOGGER.warn("ERP rejeitou o pedido id={} status={}",
                    order.getId(), exception.getStatusCode().value());
            throw new ErpClientException(
                    ErpFailure.HTTP_ERROR, "ERP retornou o status HTTP " + exception.getStatusCode().value(), exception);
        } catch (ResourceAccessException exception) {
            LOGGER.warn("ERP indisponível para o pedido id={}", order.getId());
            ErpFailure failure = isTimeout(exception) ? ErpFailure.TIMEOUT : ErpFailure.UNAVAILABLE;
            throw new ErpClientException(failure, failure.safeMessage(), exception);
        } catch (RestClientException exception) {
            throw new ErpClientException(ErpFailure.INVALID_RESPONSE, "ERP retornou uma resposta que não pôde ser interpretada", exception);
        }
    }

    private void validateResponse(Order order, ErpOrderResponse response) {
        if (response == null) {
            throw new ErpClientException(ErpFailure.INVALID_RESPONSE, "ERP retornou uma resposta vazia", null);
        }
        if (!order.getExternalId().equals(response.externalId())) {
            throw new ErpClientException(ErpFailure.INVALID_RESPONSE, "ERP retornou um identificador externo diferente do enviado", null);
        }
        if (!"ACCEPTED".equals(response.status())) {
            throw new ErpClientException(ErpFailure.INVALID_RESPONSE, "ERP não aceitou o pedido", null);
        }
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) return true;
        }
        return false;
    }
}
