package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.adapter.in.web.Problems;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import jakarta.persistence.PessimisticLockException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CallRetryTest {

    private static final UUID ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String OPERATION = "publishPlan";
    private static final String DONE = "done";
    private static final Instant NOW = Instant.parse("2026-03-15T10:00:00Z");
    private static final Pattern LOCK_TIMEOUT = Pattern.compile("lock_timeout=(\\d+)s");

    private final RecordingAuditLog auditLog = new RecordingAuditLog();
    private final CallRetry retry = new CallRetry(() -> auditLog, new FixedClock());
    private final MockHttpServletRequest withoutAVersion = new MockHttpServletRequest();
    private final MockHttpServletRequest withIfMatch = new MockHttpServletRequest();
    private final AtomicInteger tries = new AtomicInteger();

    @BeforeEach
    void setTheTraceId() {
        MDC.put(Problems.TRACE_ID, TRACE_ID);
        withIfMatch.addHeader(HttpHeaders.IF_MATCH, "\"3\"");
    }

    @AfterEach
    void clearTheMdc() {
        MDC.clear();
    }

    /** A call that throws on each of its first tries the exception that try's supplier makes. */
    private CallRetry.Call failingFirst(int failures, IntFunction<Exception> failure) {
        return () -> {
            int attempt = tries.incrementAndGet();
            if (attempt <= failures) {
                throw failure.apply(attempt);
            }
            return DONE;
        };
    }

    private static StaleVersion savesConflict() {
        return new StaleVersion("Plan", ID, new IllegalStateException("optimistic lock"));
    }

    private static StaleVersion requireCurrentsRefusal() {
        try {
            StaleVersion.requireCurrent("Plan", ID, Version.of(4), Version.of(3));
        } catch (StaleVersion refused) {
            return refused;
        }
        throw new AssertionError("requireCurrent refused nothing");
    }

    @Test
    void GivenTwoConflicts_WhenACallWithoutAVersionRuns_ThenTheThirdTrySucceeds() throws Exception {
        Object answer = retry.run(withoutAVersion, OPERATION, failingFirst(2, attempt -> savesConflict()));

        assertThat(answer).isEqualTo(DONE);
        assertThat(tries).hasValue(3);
        assertThat(auditLog.refusals).isEmpty();
    }

    @Test
    void GivenThreeConflicts_WhenACallWithoutAVersionRuns_ThenTheFourthTrySucceeds() throws Exception {
        Object answer = retry.run(withoutAVersion, OPERATION, failingFirst(3, attempt -> savesConflict()));

        assertThat(answer).isEqualTo(DONE);
        assertThat(tries).hasValue(1 + CallRetry.MORE_TRIES);
    }

    @Test
    void GivenFourConflicts_WhenACallWithoutAVersionRuns_ThenTheLastConflictIsRethrownAsARaceThatAnswers503() {
        assertThatThrownBy(() -> retry.run(withoutAVersion, OPERATION, failingFirst(4, attempt -> savesConflict())))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(Race.of(stale, false)).isEqualTo(Race.RETRY);
                    assertThat(Race.of(stale, false).status().value()).isEqualTo(503);
                });
        assertThat(tries).hasValue(1 + CallRetry.MORE_TRIES);
    }

    @Test
    void GivenALockWaitThatFailed_WhenACallWithAVersionRuns_ThenItIsRetried() throws Exception {
        Object answer = retry.run(
                withIfMatch, OPERATION, failingFirst(1, attempt -> new PessimisticLockException("lock wait")));

        assertThat(answer).isEqualTo(DONE);
        assertThat(tries).hasValue(2);
    }

    @Test
    void GivenAStaleVersionThatComparedTheRequestsVersion_WhenACallRuns_ThenItIsNotRetried() {
        assertThatThrownBy(() ->
                        retry.run(withoutAVersion, OPERATION, failingFirst(1, attempt -> requireCurrentsRefusal())))
                .isInstanceOf(StaleVersion.class);
        assertThat(tries).hasValue(1);
        assertThat(auditLog.refusals).containsExactly(new Refusal(OPERATION, TRACE_ID, NOW, StaleVersion.CODE));
    }

    @Test
    void GivenASavesStaleVersionOnACallThatSentIfMatch_WhenACallRuns_ThenItIsNotRetriedAndAnswers412() {
        assertThatThrownBy(() -> retry.run(withIfMatch, OPERATION, failingFirst(1, attempt -> savesConflict())))
                .isInstanceOfSatisfying(
                        StaleVersion.class,
                        stale -> assertThat(Race.of(stale, true)).isEqualTo(Race.PRECONDITION_FAILED));
        assertThat(tries).hasValue(1);
    }

    @Test
    void GivenASavesStaleVersionOnACallThatSentIfNoneMatch_WhenACallRuns_ThenItIsNotRetried() {
        MockHttpServletRequest withIfNoneMatch = new MockHttpServletRequest();
        withIfNoneMatch.addHeader(HttpHeaders.IF_NONE_MATCH, "*");

        assertThatThrownBy(() -> retry.run(withIfNoneMatch, OPERATION, failingFirst(1, attempt -> savesConflict())))
                .isInstanceOfSatisfying(
                        StaleVersion.class,
                        stale -> assertThat(Race.of(stale, true)).isEqualTo(Race.PRECONDITION_FAILED));
        assertThat(tries).hasValue(1);
        assertThat(auditLog.refusals).containsExactly(new Refusal(OPERATION, TRACE_ID, NOW, StaleVersion.CODE));
    }

    @Test
    void GivenARaceAfterAReadWriteCommit_WhenACallRuns_ThenItIsNeitherRetriedNorAuditedAndNoLongerARace() {
        CallRetry.Call commitThenLockWait = () -> {
            tries.incrementAndGet();
            new CommitWatch().afterCommit(new SimpleTransactionStatus(), null);
            throw new PessimisticLockException("lock wait");
        };

        assertThatThrownBy(() -> retry.run(withoutAVersion, OPERATION, commitThenLockWait))
                .isInstanceOfSatisfying(IllegalStateException.class, thrown -> {
                    assertThat(thrown).hasCauseInstanceOf(PessimisticLockException.class);
                    assertThat(Race.of(thrown, false)).isEqualTo(Race.NONE);
                });
        assertThat(tries).hasValue(1);
        assertThat(auditLog.refusals).isEmpty();
    }

    @Test
    void GivenAStaleBodyVersionAfterAReadWriteCommit_WhenACallRuns_ThenItIsAnsweredAsARaceAfterACommitNotA409() {
        CallRetry.Call commitThenStaleVersion = () -> {
            tries.incrementAndGet();
            new CommitWatch().afterCommit(new SimpleTransactionStatus(), null);
            throw requireCurrentsRefusal();
        };

        assertThatThrownBy(() -> retry.run(withoutAVersion, OPERATION, commitThenStaleVersion))
                .isInstanceOfSatisfying(IllegalStateException.class, thrown -> {
                    assertThat(thrown).hasCauseInstanceOf(StaleVersion.class);
                    assertThat(Race.of(thrown, false)).isEqualTo(Race.NONE);
                });
        assertThat(tries).hasValue(1);
        assertThat(auditLog.refusals).isEmpty();
    }

    @Test
    void GivenAStaleVersionOnACallThatSentIfMatchAfterAReadWriteCommit_WhenACallRuns_ThenItIsARaceAfterACommitNot412() {
        CallRetry.Call commitThenSavesConflict = () -> {
            tries.incrementAndGet();
            new CommitWatch().afterCommit(new SimpleTransactionStatus(), null);
            throw savesConflict();
        };

        assertThatThrownBy(() -> retry.run(withIfMatch, OPERATION, commitThenSavesConflict))
                .isInstanceOfSatisfying(IllegalStateException.class, thrown -> {
                    assertThat(thrown).hasCauseInstanceOf(StaleVersion.class);
                    assertThat(Race.of(thrown, true)).isEqualTo(Race.NONE);
                });
        assertThat(tries).hasValue(1);
        assertThat(auditLog.refusals).isEmpty();
    }

    @Test
    void GivenAStaleVersionFromASaveOnACallWithItsVersionInTheBody_WhenItIsRetried_Then409() {
        assertThatThrownBy(() -> retry.run(
                        withoutAVersion,
                        OPERATION,
                        failingFirst(2, attempt -> attempt == 1 ? savesConflict() : requireCurrentsRefusal())))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(Race.of(stale, false)).isEqualTo(Race.STALE_BODY_VERSION);
                    assertThat(Race.of(stale, false).status().value()).isEqualTo(409);
                });
        assertThat(tries).hasValue(2);
    }

    @Test
    void GivenAConflictThatEndsAs503_WhenTheCallIsAnswered_ThenOneRefusedAuditRecordNamesTheOperation() {
        assertThatThrownBy(() -> retry.run(withoutAVersion, OPERATION, failingFirst(4, attempt -> savesConflict())))
                .isInstanceOf(StaleVersion.class);

        assertThat(auditLog.refusals).containsExactly(new Refusal(OPERATION, TRACE_ID, NOW, WebCodes.TRY_AGAIN_LATER));
    }

    @Test
    void GivenAnExceptionThatIsNoRace_WhenACallRuns_ThenItIsNeitherRetriedNorAudited() {
        assertThatThrownBy(() -> retry.run(
                        withoutAVersion, OPERATION, failingFirst(1, attempt -> new IllegalStateException("broken"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(tries).hasValue(1);
        assertThat(auditLog.refusals).isEmpty();
    }

    @Test
    void GivenAnError_WhenACallRuns_ThenItPassesThroughUntouched() {
        assertThatThrownBy(() -> retry.run(withoutAVersion, OPERATION, () -> {
                    tries.incrementAndGet();
                    throw new AssertionError("an error, not an exception");
                }))
                .isInstanceOf(AssertionError.class);
        assertThat(tries).hasValue(1);
    }

    @Test
    void GivenAnAuditLogThatFails_WhenARaceIsAudited_ThenItsFailureIsThrownWithTheRaceSuppressed() {
        IllegalStateException auditDown = new IllegalStateException("the audit store is down");
        StaleVersion race = requireCurrentsRefusal();
        CallRetry failingAudit = new CallRetry(
                () -> {
                    throw auditDown;
                },
                new FixedClock());

        assertThatThrownBy(() -> failingAudit.run(withoutAVersion, OPERATION, failingFirst(1, attempt -> race)))
                .isSameAs(auditDown)
                .hasMessage("the audit store is down")
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(race));
        assertThat(tries).hasValue(1);
    }

    @Test
    void GivenNoTraceIdInTheMdc_WhenARaceIsAudited_ThenIllegalStateException() {
        MDC.clear();

        assertThatThrownBy(() ->
                        retry.run(withoutAVersion, OPERATION, failingFirst(1, attempt -> requireCurrentsRefusal())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(auditLog.refusals).isEmpty();
    }

    @Test
    void GivenTheLockTimeoutTheDatabaseIsGiven_WhenComparedWithRetryAfter_ThenTheyAgree() throws IOException {
        Properties properties = new Properties();
        try (InputStream file = getClass().getResourceAsStream("/application.properties")) {
            assertThat(file).as("application.properties").isNotNull();
            properties.load(file);
        }
        Matcher timeout =
                LOCK_TIMEOUT.matcher(properties.getProperty("spring.datasource.hikari.data-source-properties.options"));

        assertThat(timeout.find()).isTrue();
        assertThat(Integer.parseInt(timeout.group(1))).isEqualTo(Race.RETRY_AFTER_SECONDS);
    }

    record Refusal(String operation, String traceId, Instant at, String errorCode) {}

    static final class RecordingAuditLog implements AuditLog {

        final List<Refusal> refusals = new ArrayList<>();

        @Override
        public void succeeded(CallContext call, Instant at, List<AuditedRecord> records) {
            throw new AssertionError("the call wrapper records no success");
        }

        @Override
        public void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode) {
            assertThat(call.user()).isEmpty();
            assertThat(records).isEmpty();
            refusals.add(new Refusal(call.operation(), call.traceId(), at, errorCode));
        }
    }

    static final class FixedClock implements Clock {

        @Override
        public Instant now() {
            return NOW;
        }

        @Override
        public LocalDate today() {
            return LocalDate.of(2026, 3, 15);
        }
    }
}
