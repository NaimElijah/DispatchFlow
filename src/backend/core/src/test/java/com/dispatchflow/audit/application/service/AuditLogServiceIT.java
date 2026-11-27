package com.dispatchflow.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.audit.application.api.ClientKind;
import com.dispatchflow.audit.application.api.ClientRelease;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.SessionId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The wired {@link AuditLog} against a real PostgreSQL (NFR-23, ADR-110, ADR-129 decision 5,
 * ADR-149): a success commits with its change or not at all, and a refusal recorded once its change
 * has rolled back stays. The rows are read back with SQL, as {@code app}, because the core never
 * reads audit records.
 *
 * <p>The database is shared and never cleaned, so test {@code n} finds its rows by its own trace ID,
 * {@code trace(n)}, and reads them back in the test that wrote them, before another test's purge
 * could run.
 */
@IntegrationTest
class AuditLogServiceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_6c09_5e75L;
    private static final Instant AT = Instant.parse("2027-06-13T08:00:00.123456Z");
    private static final String PUBLISH_PLAN = "publishPlan";
    private static final String PLAN_HAS_VIOLATIONS = "PLAN_HAS_VIOLATIONS";

    @Autowired
    private AuditLog auditLog;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void GivenTheAuditLog_WhenSpringWiresIt_ThenItIsAuditLogService() {
        assertThat(AopUtils.getTargetClass(auditLog)).isEqualTo(AuditLogService.class);
    }

    @Test
    void GivenAMalformedSuccessThatItsChangeSwallows_WhenTheChangeCommits_ThenTheCommitFailsAndNothingIsStored() {
        // NFR-23: a change whose record Audit refused never commits without it, even when the change
        // catches the bug. The change's own write here is a record of its own.
        int n = 9;
        CallContext malformed = new CallContext(
                Optional.of(AccountId.of(key(n, 1))),
                Optional.of(SessionId.of(key(n, 2))),
                Optional.empty(),
                "not an operation name",
                trace(n));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    auditLog.succeeded(aSignedInCall(n), AT, List.of());
                    try {
                        auditLog.succeeded(malformed, AT, List.of());
                    } catch (IllegalStateException swallowed) {
                        // The change goes on as if nothing failed; the transaction is rollback-only.
                    }
                }))
                .isInstanceOf(UnexpectedRollbackException.class);

        assertThat(storedRows(n)).isEmpty();
    }

    @Test
    void GivenAChangeThatRolledBack_WhenItsRefusalIsRecordedAfterwards_ThenTheRecordStays() {
        int n = 1;
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    auditLog.succeeded(aSignedInCall(n), AT, List.of(AuditedRecord.of(plan(n))));
                    throw new RolledBack();
                }))
                .isInstanceOf(RolledBack.class);

        auditLog.refused(aSignedInCall(n), AT, List.of(AuditedRecord.of(plan(n))), PLAN_HAS_VIOLATIONS);

        assertThat(storedRows(n))
                .containsExactly(new StoredRow(
                        AT,
                        key(n, 1),
                        key(n, 2),
                        "WEB",
                        "1.4.2",
                        PUBLISH_PLAN,
                        "REFUSED",
                        PLAN_HAS_VIOLATIONS,
                        trace(n)));
        assertThat(storedNamedRecords(n)).containsExactly(new StoredNamedRecord("PLAN", key(n, 3)));
    }

    @Test
    void GivenARefusalRecordedInsideAChange_WhenTheChangeRollsBack_ThenTheRecordGoesWithIt() {
        // The trap ADR-129 decision 5 avoids: recorded inside the refused transaction, a refusal is
        // lost with it.
        int n = 2;
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    auditLog.refused(aSignedInCall(n), AT, List.of(), PLAN_HAS_VIOLATIONS);
                    throw new RolledBack();
                }))
                .isInstanceOf(RolledBack.class);

        assertThat(storedRows(n)).isEmpty();
    }

    @Test
    void GivenASuccessRecordedInAChange_WhenTheChangeCommits_ThenItsRowHoldsEveryField() {
        int n = 3;
        transactions.inTransaction(() -> auditLog.succeeded(
                aSignedInCall(n), AT, List.of(AuditedRecord.of(plan(n)), AuditedRecord.of(OrderId.of(key(n, 4))))));

        assertThat(storedRows(n))
                .containsExactly(new StoredRow(
                        AT, key(n, 1), key(n, 2), "WEB", "1.4.2", PUBLISH_PLAN, "SUCCEEDED", null, trace(n)));
        assertThat(storedNamedRecords(n))
                .containsExactly(new StoredNamedRecord("PLAN", key(n, 3)), new StoredNamedRecord("ORDER", key(n, 4)));
    }

    @Test
    void GivenASuccessRecordedInAChange_WhenTheChangeRollsBack_ThenTheRecordGoesWithIt() {
        int n = 4;
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    auditLog.succeeded(aSignedInCall(n), AT, List.of(AuditedRecord.of(plan(n))));
                    throw new RolledBack();
                }))
                .isInstanceOf(RolledBack.class);

        assertThat(storedRows(n)).isEmpty();
    }

    @Test
    void GivenARefusedSignInFromAPreReleaseClient_WhenRecorded_ThenItsRowLeavesTheCallerAndTheAppEmpty() {
        int n = 5;
        CallContext call = new CallContext(
                Optional.empty(),
                Optional.empty(),
                Optional.of(new ClientRelease(ClientKind.PHONE, "1.4.2-beta.1")),
                "signIn",
                trace(n));

        auditLog.refused(call, AT, List.of(), "ACCOUNT_DEACTIVATED");

        assertThat(storedRows(n))
                .containsExactly(new StoredRow(
                        AT, null, null, null, null, "signIn", "REFUSED", "ACCOUNT_DEACTIVATED", trace(n)));
    }

    @Test
    void GivenAReadOnlyTransaction_WhenARecordIsWrittenInIt_ThenItFailsLoudlyRatherThanLoseIt() {
        // A read-only transaction never flushes, so a record written in one would vanish silently.
        int n = 6;
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        assertThatThrownBy(() -> readOnly.executeWithoutResult(
                        status -> auditLog.refused(aSignedInCall(n), AT, List.of(), PLAN_HAS_VIOLATIONS)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() ->
                        readOnly.executeWithoutResult(status -> auditLog.succeeded(aSignedInCall(n), AT, List.of())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(storedRows(n)).isEmpty();
    }

    /** A dispatcher's call from the web app, version 1.4.2, signed in, as test {@code n}'s. */
    private static CallContext aSignedInCall(int n) {
        return new CallContext(
                Optional.of(AccountId.of(key(n, 1))),
                Optional.of(SessionId.of(key(n, 2))),
                Optional.of(new ClientRelease(ClientKind.WEB, "1.4.2")),
                PUBLISH_PLAN,
                trace(n));
    }

    private static PlanId plan(int n) {
        return PlanId.of(key(n, 3));
    }

    private static String trace(int n) {
        return "a10c%028x".formatted(n);
    }

    private static UUID key(int n, int each) {
        return new UUID(KEY_PREFIX + n, each);
    }

    private List<StoredRow> storedRows(int n) {
        return jdbc.sql("""
                        SELECT at, account, session, app, app_version, operation, outcome, error_code, trace_id
                        FROM audit.audit_record WHERE trace_id = ?
                        """)
                .param(trace(n))
                .query((row, index) -> new StoredRow(
                        row.getObject("at", OffsetDateTime.class).toInstant(),
                        row.getObject("account", UUID.class),
                        row.getObject("session", UUID.class),
                        row.getString("app"),
                        row.getString("app_version"),
                        row.getString("operation"),
                        row.getString("outcome"),
                        row.getString("error_code"),
                        row.getString("trace_id")))
                .list();
    }

    private List<StoredNamedRecord> storedNamedRecords(int n) {
        return jdbc.sql("""
                        SELECT named.kind, named.record_id
                        FROM audit.audit_record_named_record named
                        JOIN audit.audit_record stored ON stored.id = named.audit_record_id
                        WHERE stored.trace_id = ?
                        ORDER BY named.position
                        """)
                .param(trace(n))
                .query((row, index) ->
                        new StoredNamedRecord(row.getString("kind"), row.getObject("record_id", UUID.class)))
                .list();
    }

    /** One stored audit row, as SQL reads it. */
    private record StoredRow(
            Instant at,
            @Nullable UUID account,
            @Nullable UUID session,
            @Nullable String app,
            @Nullable String appVersion,
            String operation,
            String outcome,
            @Nullable String errorCode,
            String traceId) {}

    /** One stored named record, as SQL reads it. */
    private record StoredNamedRecord(String kind, UUID recordId) {}

    /** What a test throws to roll its change back. */
    private static final class RolledBack extends RuntimeException {

        RolledBack() {
            super("the change rolls back");
        }
    }
}
