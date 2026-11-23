package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class DispatchFlowApplicationIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private LoggingSystem loggingSystem;

    @Test
    void GivenAPostgresDatabase_WhenHealthIsProbed_ThenUp() {
        MvcTestResult health = mvc.get().uri("/actuator/health").exchange();

        assertThat(health).hasStatusOk();
        assertThat(health).bodyJson().extractingPath("$.status").asString().isEqualTo("UP");
    }

    /**
     * These loggers write SQL, bind values, values read or the server's error detail, all of which
     * hold personal data (NFR-19). An explicit level keeps them quiet when the root level is raised.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "org.postgresql",
                "org.hibernate.SQL",
                "org.hibernate.orm.jdbc.bind",
                "org.hibernate.orm.jdbc.extract"
            })
    void GivenALoggerThatCanQuotePersonalData_WhenItsLevelIsRead_ThenItIsSetToInfoOfItsOwn(String logger) {
        assertThat(loggingSystem.getLoggerConfiguration(logger).getConfiguredLevel())
                .isEqualTo(LogLevel.INFO);
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
