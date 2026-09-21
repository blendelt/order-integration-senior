package com.example.orders.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestTest {
    @Test
    void rejectsValuesThatWouldRoundOrOverflowDatabaseColumn() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String invalid : new String[]{"1.001", "100000000000000000.00", "0", "-1"}) {
                assertThat(validator.validate(new CreateOrderRequest("TEST", "Test", new BigDecimal(invalid))))
                        .isNotEmpty();
            }
            assertThat(validator.validate(new CreateOrderRequest("TEST", "Test", new BigDecimal("99999999999999999.99"))))
                    .isEmpty();
        }
    }
}
