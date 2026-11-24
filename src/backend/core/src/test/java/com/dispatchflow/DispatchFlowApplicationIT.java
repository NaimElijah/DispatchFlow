package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PessimisticLockException;
import java.sql.SQLException;
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

    /** PostgreSQL's SQL state for a statement its lock timeout cancelled. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    /** Takes an advisory lock for the transaction, under a key no module uses. */
    private static final String TAKE_THE_TEST_LOCK =
            "SELECT 1 FROM pg_advisory_xact_lock(hashtext('test:lock-timeout'))";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Transactions transactions;

    @Autowired
    private EntityManager entityManager;

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
     * The driver opens each session in the JVM's zone, which the build pins to {@code
     * Asia/Jerusalem}, so without the core's own setting the session would run in that zone.
     */
    @Test
    void GivenTheCoresDatabase_WhenItsSessionIsAskedItsZone_ThenUtc() {
        assertThat(jdbc.sql("SHOW TimeZone").query(String.class).single()).isEqualTo("UTC");
    }

    /** PostgreSQL's own default is 0, which means a wait for a lock has no limit (ADR-136). */
    @Test
    void GivenTheCoresDatabase_WhenItsSessionIsAskedItsLockTimeout_ThenTwoSeconds() {
        assertThat(jdbc.sql("SHOW lock_timeout").query(String.class).single()).isEqualTo("2s");
    }

    /**
     * A native query through the entity manager ends with JPA's {@code PessimisticLockException}; a
     * Spring Data query's ends with Spring's {@code PessimisticLockingFailureException}, which the
     * persistence tests show (ADR-136). The waiting
     * transaction's statement timeout is longer than the lock timeout, so it ends the wait only if
     * the core's setting is missing, and with another SQL state, which fails the test instead of
     * hanging it.
     */
    @Test
    void GivenAnotherTransactionHoldsALock_WhenTheEntityManagerWaitsForIt_ThenTheWaitEndsAsLockNotAvailable() {
        transactions.inTransaction(() -> {
            jdbc.sql(TAKE_THE_TEST_LOCK).query(Integer.class).single();
            assertThatThrownBy(() -> transactions.inANewTransaction(() -> {
                        jdbc.sql("SET LOCAL statement_timeout = '10s'").update();
                        entityManager.createNativeQuery(TAKE_THE_TEST_LOCK).getSingleResult();
                    }))
                    .isInstanceOf(PessimisticLockException.class)
                    .rootCause()
                    .isInstanceOfSatisfying(
                            SQLException.class,
                            cause -> assertThat(cause.getSQLState()).isEqualTo(LOCK_NOT_AVAILABLE));
        });
    }
}
