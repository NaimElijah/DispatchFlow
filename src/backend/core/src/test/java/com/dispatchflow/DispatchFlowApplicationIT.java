package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class DispatchFlowApplicationIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void GivenAPostgresDatabase_WhenHealthIsProbed_ThenUp() {
        MvcTestResult health = mvc.get().uri("/actuator/health").exchange();

        assertThat(health).hasStatusOk();
        assertThat(health).bodyJson().extractingPath("$.status").asString().isEqualTo("UP");
    }

    /**
     * The driver opens each session in the JVM's zone, so without the core's own setting the
     * session would run in {@code Asia/Jerusalem} on a developer's machine and in UTC in CI.
     */
    @Test
    void GivenTheCoresDatabase_WhenItsSessionIsAskedItsZone_ThenUtc() {
        assertThat(jdbc.sql("SHOW TimeZone").query(String.class).single()).isEqualTo("UTC");
    }
}
