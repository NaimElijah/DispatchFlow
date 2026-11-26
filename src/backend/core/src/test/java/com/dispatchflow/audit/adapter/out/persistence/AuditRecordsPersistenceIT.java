package com.dispatchflow.audit.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.DatabaseOwner;
import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.audit.application.port.out.AuditRecords;
import com.dispatchflow.audit.domain.AppVersion;
import com.dispatchflow.audit.domain.AuditOutcome;
import com.dispatchflow.audit.domain.AuditRecord;
import com.dispatchflow.audit.domain.ClientApp;
import com.dispatchflow.audit.domain.DeclaredApp;
import com.dispatchflow.audit.domain.ErrorCode;
import com.dispatchflow.audit.domain.NamedRecord;
import com.dispatchflow.audit.domain.OperationName;
import com.dispatchflow.audit.domain.RecordKind;
import com.dispatchflow.audit.domain.TraceId;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link AuditRecords} against a real PostgreSQL (ADR-126, ADR-127): what an appended record stores,
 * read back with SQL because the core never reads audit records through the port, and that every
 * record is kept, even one equal to another.
 *
 * <p>The database is shared and never cleaned, and a record has no key of its own, so test {@code n}
 * finds its records by its own trace ID, {@code trace(n)}.
 */
@IntegrationTest
class AuditRecordsPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_6175_6474L;
    private static final Instant AT = Instant.parse("2027-06-13T07:30:00.123456Z");
    private static final OperationName PUBLISH_PLAN = OperationName.of("publishPlan");

    @Autowired
    private AuditRecords auditRecords;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    @DatabaseOwner
    private JdbcClient owner;

    @Test
    void GivenARefusedRecordWithEveryOptionalPart_WhenAppended_ThenItsRowHoldsEachValueAndItsRecordsInOrder() {
        int n = 1;
        AuditRecord record = new AuditRecord(
                AT,
                Optional.of(AccountId.of(key(n, 1))),
                Optional.of(SessionId.of(key(n, 2))),
                Optional.of(new DeclaredApp(ClientApp.PHONE, new AppVersion(1, 4, 2))),
                PUBLISH_PLAN,
                List.of(
                        NamedRecord.of(PlanId.of(key(n, 3))),
                        NamedRecord.of(StopId.of(key(n, 5))),
                        NamedRecord.of(RouteId.of(key(n, 4)))),
                AuditOutcome.refused(ErrorCode.of("STALE_VERSION")),
                trace(n));

        transactions.inTransaction(() -> auditRecords.append(record));

        assertThat(storedRows(n))
                .containsExactly(new StoredRow(
                        AT,
                        key(n, 1),
                        key(n, 2),
                        "PHONE",
                        "1.4.2",
                        "publishPlan",
                        "REFUSED",
                        "STALE_VERSION",
                        trace(n).value()));
        assertThat(storedNamedRecords(n))
                .containsExactly(
                        new NamedRecord(RecordKind.PLAN, key(n, 3)),
                        new NamedRecord(RecordKind.STOP, key(n, 5)),
                        new NamedRecord(RecordKind.ROUTE, key(n, 4)));
    }

    @Test
    void GivenASucceededRecordWithNoOptionalPartAndNoRecords_WhenAppended_ThenItsRowLeavesThoseColumnsEmpty() {
        int n = 2;
        AuditRecord record = new AuditRecord(
                AT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OperationName.of("purgeExpiredAuditRecords"),
                List.of(),
                AuditOutcome.succeeded(),
                trace(n));

        transactions.inTransaction(() -> auditRecords.append(record));

        assertThat(storedRows(n))
                .containsExactly(new StoredRow(
                        AT, null, null, null, null, "purgeExpiredAuditRecords", "SUCCEEDED", null, trace(n).value()));
        assertThat(storedNamedRecords(n)).isEmpty();
    }

    @Test
    void GivenTwoRecordsWithTheSameContent_WhenBothAreAppended_ThenBothAreKeptWithTheirOwnRecords() {
        int n = 3;
        AuditRecord record = succeeded(n, List.of(NamedRecord.of(PlanId.of(key(n, 1)))));

        transactions.inTransaction(() -> {
            auditRecords.append(record);
            auditRecords.append(record);
        });

        assertThat(storedRows(n)).hasSize(2);
        assertThat(storedNamedRecords(n))
                .containsExactly(
                        new NamedRecord(RecordKind.PLAN, key(n, 1)), new NamedRecord(RecordKind.PLAN, key(n, 1)));
    }

    @Test
    void GivenARecordNamingTheMostRecordsAllowed_WhenAppended_ThenEveryOneIsKeptInOrder() {
        int n = 4;
        List<NamedRecord> named = IntStream.range(0, AuditRecord.MAX_RECORDS)
                .mapToObj(each -> NamedRecord.of(StopId.of(key(n, each))))
                .toList();

        transactions.inTransaction(() -> auditRecords.append(succeeded(n, named)));

        assertThat(storedNamedRecords(n)).containsExactlyElementsOf(named);
    }

    @Test
    void GivenAnAppendInATransactionThatRollsBack_WhenTheTransactionEnds_ThenNothingIsKept() {
        int n = 5;

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    auditRecords.append(succeeded(n, List.of()));
                    throw new RolledBack();
                }))
                .isInstanceOf(RolledBack.class);

        assertThat(storedRows(n)).isEmpty();
    }

    @Test
    void GivenAStoredRefusal_WhenItsCodeIsRemovedWithSql_ThenTheDatabaseRefusesTheWrite() {
        int n = 6;
        transactions.inTransaction(() -> auditRecords.append(new AuditRecord(
                AT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                PUBLISH_PLAN,
                List.of(),
                AuditOutcome.refused(ErrorCode.of("STALE_VERSION")),
                trace(n))));

        assertThatThrownBy(() -> owner.sql("UPDATE audit.audit_record SET error_code = NULL WHERE trace_id = ?")
                        .param(trace(n).value())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void GivenAStoredRecordFromAnApp_WhenItsVersionIsRemovedWithSql_ThenTheDatabaseRefusesTheWrite() {
        int n = 7;
        transactions.inTransaction(() -> auditRecords.append(new AuditRecord(
                AT,
                Optional.empty(),
                Optional.empty(),
                Optional.of(new DeclaredApp(ClientApp.WEB, new AppVersion(2, 0, 0))),
                PUBLISH_PLAN,
                List.of(),
                AuditOutcome.succeeded(),
                trace(n))));

        assertThatThrownBy(() -> owner.sql("UPDATE audit.audit_record SET app_version = NULL WHERE trace_id = ?")
                        .param(trace(n).value())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** The test's own failure, which rolls its transaction back. */
    private static final class RolledBack extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** One stored audit record's columns, as SQL reads them. */
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

    private static AuditRecord succeeded(int n, List<NamedRecord> named) {
        return new AuditRecord(
                AT,
                Optional.of(AccountId.of(key(n, 0))),
                Optional.empty(),
                Optional.of(new DeclaredApp(ClientApp.WEB, new AppVersion(1, 0, 0))),
                PUBLISH_PLAN,
                named,
                AuditOutcome.succeeded(),
                trace(n));
    }

    /** Test {@code n}'s own trace ID: 32 lower-case hex digits, never all zero. */
    private static TraceId trace(int n) {
        return TraceId.of("a0d1%028x".formatted(n));
    }

    private static UUID key(int n, int each) {
        return new UUID(KEY_PREFIX + n, each);
    }

    private List<StoredRow> storedRows(int n) {
        return jdbc.sql("""
                        SELECT at, account, session, app, app_version, operation, outcome, error_code, trace_id
                        FROM audit.audit_record WHERE trace_id = ?
                        """)
                .param(trace(n).value())
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

    /** Test {@code n}'s named records, each record's in its order, the records in no order of their own. */
    private List<NamedRecord> storedNamedRecords(int n) {
        return jdbc.sql("""
                        SELECT named.kind, named.record_id
                        FROM audit.audit_record_named_record named
                        JOIN audit.audit_record stored ON stored.id = named.audit_record_id
                        WHERE stored.trace_id = ?
                        ORDER BY stored.id, named.position
                        """)
                .param(trace(n).value())
                .query((row, index) -> new NamedRecord(
                        RecordKind.valueOf(row.getString("kind")), row.getObject("record_id", UUID.class)))
                .list();
    }
}
