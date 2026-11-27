package com.dispatchflow.audit.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.TestActors;
import com.dispatchflow.orders.domain.OrdersError;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.RuleViolations;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class RefusalsTest {

    private static final UUID ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final Instant AT = Instant.parse("2026-03-15T08:00:00Z");
    private static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2026, 3, 15));
    private static final CallContext CALL = CallContext.serverStep("refusalsTest", TestActors.TRACE_ID);
    private static final List<AuditedRecord> RECORDS = List.of(AuditedRecord.of(PlanId.of(ID)));

    private final RecordingAuditLog auditLog = new RecordingAuditLog();

    private <T> T recorded(Supplier<T> attempt) {
        return Refusals.recordedBy(auditLog, CALL, AT, RECORDS, attempt);
    }

    @Test
    void GivenAnAttemptThatSucceeds_WhenRun_ThenItsResultIsReturnedAndNothingIsRecorded() {
        assertThat(recorded(() -> "planned")).isEqualTo("planned");

        assertThat(auditLog.refusals).isEmpty();
    }

    @Test
    void GivenARuleViolationLeavingTheAttempt_WhenRun_ThenItIsRecordedWithItsCodeAndTheRecordsAndRethrown() {
        RuleViolation refusal = new RuleViolation(new OrdersError.DateInPast(DATE));

        assertThatThrownBy(() -> recorded(() -> {
                    throw refusal;
                }))
                .isSameAs(refusal);

        assertThat(auditLog.refusals).containsExactly(new Refusal(CALL, AT, RECORDS, "DATE_IN_PAST"));
    }

    @Test
    void GivenRuleViolationsLeavingTheAttempt_WhenRun_ThenTheyAreRecordedWithTheSummarysCodeAndRethrown() {
        RuleViolations refusals = violations();

        assertThatThrownBy(() -> recorded(() -> {
                    throw refusals;
                }))
                .isSameAs(refusals);

        assertThat(auditLog.refusals)
                .containsExactly(
                        new Refusal(CALL, AT, RECORDS, refusals.summary().code()));
    }

    @Test
    void GivenANotFoundLeavingTheAttempt_WhenRun_ThenItIsRecordedAsNotFoundAndRethrown() {
        NotFound missing = new NotFound("Plan", ID);

        assertThatThrownBy(() -> recorded(() -> {
                    throw missing;
                }))
                .isSameAs(missing);

        assertThat(auditLog.refusals).containsExactly(new Refusal(CALL, AT, RECORDS, NotFound.CODE));
    }

    @Test
    void GivenARaceLeavingTheAttempt_WhenRun_ThenItPassesThroughUnrecordedForTheCallWrapper() {
        StaleVersion race = new StaleVersion("Plan", ID);

        assertThatThrownBy(() -> recorded(() -> {
                    throw race;
                }))
                .isSameAs(race);

        assertThat(auditLog.refusals).isEmpty();
    }

    @Test
    void GivenABugLeavingTheAttempt_WhenRun_ThenItPassesThroughUnrecorded() {
        IllegalStateException bug = new IllegalStateException("a bug");

        assertThatThrownBy(() -> recorded(() -> {
                    throw bug;
                }))
                .isSameAs(bug);

        assertThat(auditLog.refusals).isEmpty();
    }

    @Test
    void GivenTheRecordsWriteFails_WhenARefusalLeavesTheAttempt_ThenTheFailureIsThrownWithTheRefusalSuppressed() {
        IllegalStateException down = new IllegalStateException("the audit trail is down");
        AuditLog failing = new FailingAuditLog(down);
        RuleViolation refusal = new RuleViolation(new OrdersError.DateInPast(DATE));

        assertThatThrownBy(() -> Refusals.recordedBy(failing, CALL, AT, RECORDS, () -> {
                    throw refusal;
                }))
                .isSameAs(down)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(refusal));
    }

    @Test
    void GivenAnAttemptThatReturnsNothing_WhenItSucceedsOrIsRefused_ThenOnlyTheRefusalIsRecorded() {
        List<String> ran = new ArrayList<>();
        Runnable succeeds = () -> ran.add("planned");
        Runnable refused = () -> {
            throw new NotFound("Plan", ID);
        };

        Refusals.recordedBy(auditLog, CALL, AT, RECORDS, succeeds);
        assertThatThrownBy(() -> Refusals.recordedBy(auditLog, CALL, AT, RECORDS, refused))
                .isInstanceOf(NotFound.class);

        assertThat(ran).containsExactly("planned");
        assertThat(auditLog.refusals).containsExactly(new Refusal(CALL, AT, RECORDS, NotFound.CODE));
    }

    @Test
    void GivenAMissingArgument_WhenRun_ThenItFailsLoudlyNamingItBeforeTheAttemptRuns() {
        List<String> ran = new ArrayList<>();
        Supplier<String> attempt = () -> {
            ran.add("ran");
            return "planned";
        };

        assertThatThrownBy(() -> Refusals.recordedBy(null, CALL, AT, RECORDS, attempt))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("auditLog");
        assertThatThrownBy(() -> Refusals.recordedBy(auditLog, null, AT, RECORDS, attempt))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("call");
        assertThatThrownBy(() -> Refusals.recordedBy(auditLog, CALL, null, RECORDS, attempt))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("at");
        assertThatThrownBy(() -> Refusals.recordedBy(auditLog, CALL, AT, null, attempt))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("records");
        assertThatThrownBy(() -> Refusals.recordedBy(auditLog, CALL, AT, RECORDS, (Supplier<String>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("attempt");
        assertThatThrownBy(() -> Refusals.recordedBy(auditLog, CALL, AT, RECORDS, (Runnable) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("attempt");
        assertThat(ran).isEmpty();
    }

    private static RuleViolations violations() {
        try {
            RuleViolations.throwIfAny(new OrdersError.ImportHasErrors(1), List.of(new OrdersError.DateInPast(DATE)));
        } catch (RuleViolations thrown) {
            return thrown;
        }
        throw new AssertionError("nothing was thrown");
    }

    private record Refusal(CallContext call, Instant at, List<AuditedRecord> records, String errorCode) {}

    /** Records every refusal it is given. */
    private static final class RecordingAuditLog implements AuditLog {

        final List<Refusal> refusals = new ArrayList<>();

        @Override
        public void succeeded(CallContext call, Instant at, List<AuditedRecord> records) {
            throw new AssertionError("the helper records no success");
        }

        @Override
        public void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode) {
            refusals.add(new Refusal(call, at, records, errorCode));
        }
    }

    /** An audit log whose store is down: each refused record fails with the given failure. */
    private static final class FailingAuditLog implements AuditLog {

        private final RuntimeException failure;

        FailingAuditLog(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void succeeded(CallContext call, Instant at, List<AuditedRecord> records) {
            throw new AssertionError("the helper records no success");
        }

        @Override
        public void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode) {
            throw failure;
        }
    }
}
