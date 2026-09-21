package com.example.erp.service;

import com.example.erp.config.ErpSimulationProperties;
import com.example.erp.dto.ErpOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class ErpOrderServiceTest {
    private final ErpOrderService service = new ErpOrderService(new ErpSimulationProperties(0, 0, "FAIL-", 0));

    @Test void returnsPreviousAcceptanceForSameRequest() {
        var request = new ErpOrderRequest("A", "Cliente", BigDecimal.TEN);
        var first = service.process(request);
        assertThat(service.process(request)).isSameAs(first);
    }

    @Test void refusesChangingPreviouslyAcceptedOrder() {
        service.process(new ErpOrderRequest("A", "Cliente", BigDecimal.TEN));
        assertThatThrownBy(() -> service.process(new ErpOrderRequest("A", "Cliente", BigDecimal.ONE)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
}
