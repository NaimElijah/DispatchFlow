package com.dispatchflow.audit.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.DatabaseOwner;
import com.dispatchflow.DatabaseRoles;
import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.audit.application.port.out.AuditRecords;
import com.dispatchflow.audit.domain.AuditOutcome;
import com.dispatchflow.audit.domain.AuditRecord;
import com.dispatchflow.audit.domain.NamedRecord;
import com.dispatchflow.audit.domain.OperationName;
import com.dispatchflow.audit.domain.TraceId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.PlanId;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The audit trail's two guarantees, which the database keeps rather than the code (NFR-23, NFR-258,
 * ADR-041, ADR-075, ADR-149): the core's role, {@code app}, can neither change nor delete a record,
 * and the purge function, the one way a record goes, deletes only records older than a year, by the
 * same cutoff as {@code AuditRecord.retentionCutoff}. {@code app} may run the purge and nothing else
 * on Audit; the cutoff function is the owner's alone.
 *
 * <p>The database is shared and never cleaned, so test {@code n} finds its rows by its own trace ID,
 * {@code trace(n)}. Rows planted relative to the database's own {@code now()} are planted as the
 * owner, because only the owner may write an audit row by SQL.
 */
@IntegrationTest
class AuditTrailGuaranteesIT {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final long KEY_PREFIX = 0x0190a5e2_6775_6172L;
    private static final Instant AT = Instant.parse("2027-06-13T09:00:00.123456Z");
    private static final String PURGE = "SELECT audit.purge_expired_records()";
    private static final String CUTOFF = "SELECT audit.retention_cutoff(:asked)";

    /** The range the comparison covers: every clock change and leap day of these years. */
    private static final int FIRST_YEAR = 2026;

    private static final int LAST_YEAR = 2036;

    @Autowired
    private AuditRecords auditRecords;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    @DatabaseOwner
    private JdbcClient owner;

    @Autowired
    private PostgreSQLContainer postgresContainer;

    static Stream<Arguments> changesAppMayNotMake() {
        String ownRecord = "SELECT id FROM audit.audit_record WHERE trace_id = :trace";
        return Stream.of(
                Arguments.of(1, "UPDATE audit.audit_record SET operation = 'rewritten' WHERE trace_id = :trace"),
                Arguments.of(2, "DELETE FROM audit.audit_record WHERE trace_id = :trace"),
                Arguments.of(
                        3,
                        "UPDATE audit.audit_record_named_record SET kind = 'ORDER' WHERE audit_record_id IN ("
                                + ownRecord + ")"),
                Arguments.of(
                        4, "DELETE FROM audit.audit_record_named_record WHERE audit_record_id IN (" + ownRecord + ")"));
    }

    @ParameterizedTest
    @MethodSource("changesAppMayNotMake")
    void GivenAStoredRecord_WhenAppUpdatesOrDeletesIt_ThenInsufficientPrivilege(int n, String change) {
        transactions.inTransaction(() -> auditRecords.append(aRecord(n)));

        assertThatThrownBy(() -> jdbc.sql(change).param("trace", trace(n)).update())
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        refused -> assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
        assertThat(storedOperations(n)).containsExactly("publishPlan");
        assertThat(namedRecordsOf(n)).isOne();
    }

    @Test
    void GivenRecordsJustOlderAndJustYoungerThanAYear_WhenAppRunsThePurge_ThenOnlyTheOlderAreDeleted() {
        int older = 10;
        int younger = 11;
        plantAgainstTheCutoff(older, "-1 minute");
        plantAgainstTheCutoff(younger, "1 minute");
        int expired = owner.sql(
                        "SELECT count(*)::integer FROM audit.audit_record WHERE at < audit.retention_cutoff(now())")
                .query(Integer.class)
                .single();

        int purged = jdbc.sql(PURGE).query(Integer.class).single();

        assertThat(purged).isEqualTo(expired).isPositive();
        assertThat(storedOperations(older)).isEmpty();
        assertThat(namedRecordsOf(older)).isZero();
        assertThat(storedOperations(younger)).containsExactly("purgeProbe");
        assertThat(namedRecordsOf(younger)).isOne();
    }

    @ParameterizedTest
    @CsvSource({
        // 30 October 2028 is in winter time and 30 October 2027 was in summer time.
        "2028-10-30T10:00:00Z, 2027-10-30T09:00:00Z",
        // 25 March 2028 is in summer time and 25 March 2027 was in winter time.
        "2028-03-25T09:00:00Z, 2027-03-25T10:00:00Z"
    })
    void GivenAnInstantInWinterTimeWhoseYearAgoWasSummerTimeOrTheReverse_WhenTheCutoffIsComputed_ThenItMatchesTheDomain(
            Instant asked, Instant cutoff) {
        Instant computed = cutoffOf(asked);

        assertThat(computed).isEqualTo(cutoff).isEqualTo(AuditRecord.retentionCutoff(asked));
        assertThat(aRecordAt(computed.minus(1, ChronoUnit.MICROS)).isPastRetention(asked))
                .isTrue();
        assertThat(aRecordAt(computed).isPastRetention(asked)).isFalse();
    }

    @Test
    void
            GivenEveryHalfHourAroundEachIsraelClockChangeAndLeapDayTo2036_WhenTheCutoffIsComputed_ThenItEqualsTheDomains() {
        List<Instant> asked = askedAroundEveryClockChangeAndLeapDay();
        String array = asked.stream().map(Instant::toString).collect(Collectors.joining(",", "{", "}"));

        List<Instant> computed = owner.sql("""
                        SELECT audit.retention_cutoff(asked)
                        FROM unnest(CAST(:asked AS timestamptz[])) WITH ORDINALITY AS each(asked, position)
                        ORDER BY position
                        """)
                .param("asked", array)
                .query((row, index) -> row.getObject(1, OffsetDateTime.class).toInstant())
                .list();

        assertThat(computed).hasSameSizeAs(asked);
        List<String> mismatches = IntStream.range(0, asked.size())
                .filter(each -> !computed.get(each).equals(AuditRecord.retentionCutoff(asked.get(each))))
                .mapToObj(each -> asked.get(each) + ": database " + computed.get(each) + ", domain "
                        + AuditRecord.retentionCutoff(asked.get(each)))
                .limit(20)
                .toList();
        assertThat(mismatches).isEmpty();
    }

    @Test
    void GivenARoleOtherThanApp_WhenItRunsThePurgeOrTheCutoff_ThenInsufficientPrivilege() {
        // The container's own superuser makes the role, since neither app nor the owner may. Its
        // search_path in this database is the catalog alone, so every name below is qualified.
        JdbcClient superuser =
                JdbcClient.create(connectingAs(postgresContainer.getUsername(), postgresContainer.getPassword()));
        String role = "audit_purge_probe";
        String password = UUID.randomUUID().toString();
        runFormatted(superuser, "CREATE ROLE %I LOGIN NOSUPERUSER NOINHERIT PASSWORD %L", role, password);
        try {
            // Without USAGE on the schema the role would be refused for the schema, with the same
            // code, and prove nothing about the functions.
            runFormatted(superuser, "GRANT USAGE ON SCHEMA audit TO %I", role);
            JdbcClient probe = JdbcClient.create(connectingAs(role, password));

            assertThat(probe.sql("SELECT pg_catalog.has_schema_privilege('audit', 'USAGE')")
                            .query(Boolean.class)
                            .single())
                    .isTrue();
            assertRefusedForPrivilege(
                    () -> probe.sql(PURGE).query(Integer.class).single());
            assertRefusedForPrivilege(() -> probe.sql("SELECT audit.retention_cutoff(pg_catalog.now())")
                    .query(OffsetDateTime.class)
                    .single());
        } finally {
            runFormatted(superuser, "REVOKE USAGE ON SCHEMA audit FROM %I", role);
            runFormatted(superuser, "DROP ROLE %I", role);
        }
    }

    @Test
    void
            GivenTheAuditFunctions_WhenTheCatalogIsRead_ThenThePurgeRunsAsTheOwnerWithAFixedSearchPathAndTheCutoffIsStable() {
        List<StoredFunction> functions = owner.sql("""
                        SELECT p.proname, pg_get_userbyid(p.proowner) AS owner, p.prosecdef, p.provolatile,
                            array_to_string(p.proconfig, ';') AS settings,
                            has_function_privilege(:app, p.oid, 'EXECUTE') AS app_may_run
                        FROM pg_proc p
                        WHERE p.pronamespace = CAST('audit' AS regnamespace)
                        ORDER BY p.proname
                        """)
                .param("app", DatabaseRoles.APP)
                .query((row, index) -> new StoredFunction(
                        row.getString("proname"),
                        row.getString("owner"),
                        row.getBoolean("prosecdef"),
                        row.getString("provolatile"),
                        Optional.ofNullable(row.getString("settings")),
                        row.getBoolean("app_may_run")))
                .list();

        assertThat(functions)
                .containsExactly(
                        new StoredFunction(
                                "purge_expired_records",
                                DatabaseRoles.OWNER,
                                true,
                                "v",
                                Optional.of("search_path=pg_catalog, pg_temp"),
                                true),
                        new StoredFunction(
                                "retention_cutoff", DatabaseRoles.OWNER, false, "s", Optional.empty(), false));
    }

    /** The instants a retention run might ask at, every half hour around each change, with and without a microsecond. */
    private static List<Instant> askedAroundEveryClockChangeAndLeapDay() {
        List<Instant> centres = new ArrayList<>();
        Instant from =
                LocalDate.of(FIRST_YEAR, 1, 1).atStartOfDay(IsraelDateTime.ZONE).toInstant();
        Instant until = LocalDate.of(LAST_YEAR + 1, 1, 1)
                .atStartOfDay(IsraelDateTime.ZONE)
                .toInstant();
        for (ZoneOffsetTransition change = IsraelDateTime.ZONE.getRules().nextTransition(from);
                change != null && change.getInstant().isBefore(until);
                change = IsraelDateTime.ZONE.getRules().nextTransition(change.getInstant())) {
            // Asked near a change itself, and a year later, when the cutoff lands near it.
            centres.add(change.getInstant());
            centres.add(change.getDateTimeBefore()
                    .atZone(IsraelDateTime.ZONE)
                    .plusYears(1)
                    .toInstant());
        }
        for (int year = FIRST_YEAR; year <= LAST_YEAR + 1; year++) {
            centres.add(
                    LocalDate.of(year, 3, 1).atStartOfDay(IsraelDateTime.ZONE).toInstant());
        }
        Duration step = Duration.ofMinutes(30);
        Duration reach = Duration.ofHours(30);
        return centres.stream()
                .flatMap(centre ->
                        Stream.iterate(centre.minus(reach), at -> !at.isAfter(centre.plus(reach)), at -> at.plus(step)))
                .flatMap(at -> Stream.of(at, at.plus(1, ChronoUnit.MICROS)))
                .distinct()
                .toList();
    }

    private Instant cutoffOf(Instant asked) {
        return owner.sql(CUTOFF)
                .param("asked", OffsetDateTime.ofInstant(asked, ZoneOffset.UTC))
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
    }

    /** Plants test {@code n}'s record, with one named record, {@code shift} from the cutoff of now. */
    private void plantAgainstTheCutoff(int n, String shift) {
        UUID id = key(n, 0);
        owner.sql("""
                        INSERT INTO audit.audit_record (id, at, operation, outcome, trace_id)
                        VALUES (:id, audit.retention_cutoff(now()) + CAST(:shift AS interval), 'purgeProbe',
                            'SUCCEEDED', :trace)
                        """)
                .param("id", id)
                .param("shift", shift)
                .param("trace", trace(n))
                .update();
        owner.sql("""
                        INSERT INTO audit.audit_record_named_record (audit_record_id, position, kind, record_id)
                        VALUES (:id, 0, 'PLAN', :record)
                        """).param("id", id).param("record", key(n, 1)).update();
    }

    private List<String> storedOperations(int n) {
        return jdbc.sql("SELECT operation FROM audit.audit_record WHERE trace_id = :trace")
                .param("trace", trace(n))
                .query(String.class)
                .list();
    }

    private int namedRecordsOf(int n) {
        return jdbc.sql("""
                        SELECT count(*)::integer FROM audit.audit_record_named_record named
                        JOIN audit.audit_record stored ON stored.id = named.audit_record_id
                        WHERE stored.trace_id = :trace
                        """).param("trace", trace(n)).query(Integer.class).single();
    }

    /**
     * Runs a statement whose names and literals PostgreSQL itself quotes, through {@code
     * pg_catalog.format}, because a role's name and password can't be bound parameters.
     */
    private static void runFormatted(JdbcClient client, String template, String... values) {
        List<Object> arguments = new ArrayList<>(List.of(template));
        arguments.addAll(List.of(values));
        String statement = client.sql("SELECT pg_catalog.format(?" + ", ?".repeat(values.length) + ")")
                .params(arguments)
                .query(String.class)
                .single();
        client.sql(statement).update();
    }

    private DataSource connectingAs(String user, String password) {
        return new SimpleDriverDataSource(new Driver(), postgresContainer.getJdbcUrl(), user, password);
    }

    private static void assertRefusedForPrivilege(ThrowingCallable call) {
        assertThatThrownBy(call)
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        refused -> assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    private static AuditRecord aRecord(int n) {
        return new AuditRecord(
                AT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OperationName.of("publishPlan"),
                List.of(NamedRecord.of(PlanId.of(key(n, 1)))),
                AuditOutcome.succeeded(),
                TraceId.of(trace(n)));
    }

    private static AuditRecord aRecordAt(Instant at) {
        return new AuditRecord(
                at,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OperationName.of("publishPlan"),
                List.of(),
                AuditOutcome.succeeded(),
                TraceId.of(trace(0)));
    }

    private static String trace(int n) {
        return "a7a1%028x".formatted(n);
    }

    private static UUID key(int n, int each) {
        return new UUID(KEY_PREFIX + n, each);
    }

    /** One of Audit's functions, as the catalog describes it. */
    private record StoredFunction(
            String name,
            String owner,
            boolean runsAsItsOwner,
            String volatility,
            Optional<String> settings,
            boolean appMayRun) {}
}
