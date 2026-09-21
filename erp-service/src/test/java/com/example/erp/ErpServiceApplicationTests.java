package com.example.erp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "erp.simulation.minimum-delay-ms=0",
        "erp.simulation.maximum-delay-ms=0"
})
class ErpServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
