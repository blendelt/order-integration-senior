package com.example.erp;

import com.example.erp.config.ErpSimulationProperties;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErpSimulationPropertiesTest {
    @Test
    void rejectsNonFiniteRateAndOverflowingDelay() {
        assertThatThrownBy(() -> new ErpSimulationProperties(0, 0, "FAIL-", Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ErpSimulationProperties(0, Long.MAX_VALUE, "FAIL-", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
