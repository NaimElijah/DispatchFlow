package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class DispatchFlowApplicationIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void GivenAPostgresDatabase_WhenHealthIsProbed_ThenUp() {
        MvcTestResult health = mvc.get().uri("/actuator/health").exchange();

        assertThat(health).hasStatusOk();
        assertThat(health).bodyJson().extractingPath("$.status").asString().isEqualTo("UP");
    }
}
