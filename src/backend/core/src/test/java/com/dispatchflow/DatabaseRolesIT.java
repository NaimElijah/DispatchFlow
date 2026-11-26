package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.output.FrameConsumerResultCallback;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ADR-041's two database roles as ADR-145 sets them up: {@code dispatchflow_owner} runs Flyway and
 * owns every schema and table, the core connects as {@code app}, which owns nothing and holds only
 * what the migrations grant it, and the {@code migrate} command alone runs Flyway outside the tests.
 * Every {@code *PersistenceIT} running its adapter calls as {@code app} proves the grants are enough;
 * these tests prove the roles, the migrate command and that the grants go no further.
 *
 * <p>The tests that migrate start an empty PostgreSQL of their own, with the same init script.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class DatabaseRolesIT {

    /** PostgreSQL's SQL state for a statement its role has no privilege for. */
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    /** PostgreSQL's SQL state for a login whose password is wrong. */
    private static final String INVALID_PASSWORD = "28P01";

    /** The eight module schemas, which V1 creates (ADR-041). */
    private static final List<String> MODULE_SCHEMAS =
            List.of("access", "audit", "fleet", "liveops", "messaging", "orders", "planning", "reporting");

    /** The tables whose rows stay as written, which app may never update or delete (V11). */
    private static final List<String> RECORDS_KEPT_AS_WRITTEN = List.of(
            "audit.audit_record",
            "audit.audit_record_named_record",
            "planning.plan_revision",
            "planning.plan_revision_route",
            "planning.plan_revision_stop");

    /** Flyway's history, which the owner keeps in public and the core never reads. */
    private static final String FLYWAY_HISTORY = "public.flyway_schema_history";

    /**
     * Turns on, for one psql session, the server settings that would log a statement's text, the
     * ones the init script turns off by name; {@link #everyStatementLogged} adds the parse trees'.
     */
    private static final String STATEMENTS_LOGGED = String.join(
            " ",
            "-c log_statement=all",
            "-c log_min_duration_statement=0",
            "-c log_min_duration_sample=0",
            "-c log_transaction_sample_rate=1",
            "-c log_min_error_statement=debug5");

    /** What the server log check logs once the script has run, so it knows it has read that far. */
    private static final String LOG_CHECK_DONE = "DatabaseRolesIT: the init script log check is done";

    private static final String LOG_THAT_THE_CHECK_IS_DONE = "DO $$ BEGIN RAISE LOG '" + LOG_CHECK_DONE + "'; END $$";

    /** How long the server log check waits for its own line, which a healthy container logs at once. */
    private static final int LOG_WAIT_SECONDS = 30;

    private static final Pattern MIGRATION_FILE = Pattern.compile("V([0-9_]+)__.+\\.sql");
    private static final UUID FIXTURE_LEDGER = UUID.fromString("0190a5e2-0160-7000-8000-000000000001");
    private static final UUID FIXTURE_ENTRY = UUID.fromString("0190a5e2-0160-7000-8000-000000000002");

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    @DatabaseOwner
    private JdbcClient owner;

    @Autowired
    private Transactions transactions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DatabaseRoles roles;

    @Autowired
    private PostgreSQLContainer postgresContainer;

    static List<String> moduleSchemas() {
        return MODULE_SCHEMAS;
    }

    @ParameterizedTest
    @MethodSource("moduleSchemas")
    void GivenTheAppRole_WhenItCreatesATableInAModuleSchema_ThenItIsRefusedWithInsufficientPrivilege(String schema) {
        assertThatThrownBy(() -> jdbc.sql("CREATE TABLE " + schema + ".made_by_app (id integer)")
                        .update())
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        refused -> assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    /**
     * An owner can always grant itself back whatever was revoked, so app must own nothing at all, and
     * must be no member of the owner, which would give it the owner's rights without owning a thing.
     * {@code pg_shdepend} records the owner of every object that can have one of its own, large
     * objects included; an index or a TOAST table takes its table's. The owner's own rows show the
     * query reads them.
     */
    @Test
    void GivenTheMigratedDatabase_WhenItsTablesAndSchemasAreListed_ThenAppOwnsNone() {
        List<String> ownedByApp = objectsOwnedBy(DatabaseRoles.APP);
        List<String> ownedByTheOwner = objectsOwnedBy(DatabaseRoles.OWNER);
        boolean appIsAMemberOfTheOwner = owner.sql("SELECT pg_has_role(:app, :owner, 'MEMBER')")
                .param("app", DatabaseRoles.APP)
                .param("owner", DatabaseRoles.OWNER)
                .query(Boolean.class)
                .single();
        List<String> ownersInTheModuleSchemas = owner.sql("""
                        SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname IN (:schemas)
                        UNION ALL
                        SELECT pg_get_userbyid(c.relowner) FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname IN (:schemas)
                        """)
                .param("schemas", MODULE_SCHEMAS)
                .query(String.class)
                .list();

        assertThat(ownedByApp).isEmpty();
        assertThat(ownedByTheOwner).contains("schema audit", "table audit.audit_record");
        assertThat(appIsAMemberOfTheOwner).isFalse();
        assertThat(ownersInTheModuleSchemas)
                .hasSizeGreaterThan(MODULE_SCHEMAS.size())
                .containsOnly(DatabaseRoles.OWNER);
    }

    /** Every object {@code role} owns, in this database or the cluster, as PostgreSQL describes it. */
    private List<String> objectsOwnedBy(String role) {
        return owner.sql("""
                        SELECT pg_describe_object(classid, objid, objsubid) FROM pg_shdepend
                        WHERE refclassid = 'pg_authid'::regclass AND refobjid = CAST(:role AS regrole)
                            AND deptype = 'o'
                            AND dbid IN (0, (SELECT oid FROM pg_database WHERE datname = current_database()))
                        """).param("role", role).query(String.class).list();
    }

    @ParameterizedTest
    @MethodSource("moduleSchemas")
    void GivenTheAppRole_WhenItLooksUpEachModuleSchema_ThenUsageIsGranted(String schema) {
        assertThat(jdbc.sql("SELECT has_schema_privilege(:schema, 'USAGE')")
                        .param("schema", schema)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    /**
     * ADR-145 and ADR-146: a table that grants app nothing is one a migration forgot, and one that lets
     * app truncate it, reference it from a table of its own, put a trigger on it, maintain it or pass
     * a privilege on holds more than the core runs. A function every role may execute, or a sequence
     * app may use, is one whose migration forgot to say who may. It covers every table, sequence and
     * function the owner owns, in whichever schema, the fixture's and public's too, so a module added
     * later is covered without editing this test. A grant on one column counts as one on the table. A
     * partition answers through its parent, so it needs no grant of its own, but may hold no more than
     * one; Flyway's history may grant app nothing at all. A temporary table is a session's own.
     */
    @Test
    void GivenEveryTableTheOwnerOwns_WhenAppsPrivilegesAreRead_ThenEachGrantsSomeAndNoneMoreThanItsRows() {
        List<String> functionsOpenToAll = owner.sql("""
                        SELECT p.pronamespace::regnamespace || '.' || p.proname
                            || '(' || pg_get_function_identity_arguments(p.oid) || ')'
                        FROM pg_proc p
                        WHERE pg_get_userbyid(p.proowner) = :owner AND EXISTS (
                            SELECT 1 FROM aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) AS acl
                            WHERE acl.grantee = 0 AND acl.privilege_type = 'EXECUTE')
                        """)
                .param("owner", DatabaseRoles.OWNER)
                .query(String.class)
                .list();
        List<TablePrivileges> tables = owner.sql("""
                        SELECT n.nspname || '.' || c.relname AS table_name, c.relispartition AS is_partition,
                            has_table_privilege(:app, c.oid, 'SELECT, INSERT, UPDATE, DELETE')
                                OR has_any_column_privilege(:app, c.oid, 'SELECT, INSERT, UPDATE') AS on_rows,
                            has_table_privilege(:app, c.oid, 'TRUNCATE, REFERENCES, TRIGGER, MAINTAIN,
                                    SELECT WITH GRANT OPTION, INSERT WITH GRANT OPTION,
                                    UPDATE WITH GRANT OPTION, DELETE WITH GRANT OPTION')
                                OR has_any_column_privilege(:app, c.oid, 'REFERENCES, SELECT WITH GRANT OPTION,
                                    INSERT WITH GRANT OPTION, UPDATE WITH GRANT OPTION') AS beyond_rows
                        FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE c.relkind IN ('r', 'p', 'v', 'm', 'f') AND c.relpersistence <> 't'
                            AND pg_get_userbyid(c.relowner) = :owner
                        """)
                .param("app", DatabaseRoles.APP)
                .param("owner", DatabaseRoles.OWNER)
                .query(TablePrivileges.class)
                .list();
        List<String> sequencesAppMayUse = owner.sql("""
                        SELECT n.nspname || '.' || c.relname FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE c.relkind = 'S' AND pg_get_userbyid(c.relowner) = :owner
                            -- PostgreSQL may weigh the conditions in any order, and this one refuses
                            -- anything but a sequence.
                            AND CASE WHEN c.relkind = 'S'
                                THEN has_sequence_privilege(:app, c.oid, 'USAGE, SELECT, UPDATE') END
                        """)
                .param("app", DatabaseRoles.APP)
                .param("owner", DatabaseRoles.OWNER)
                .query(String.class)
                .list();

        // Soft, so one run names every table a migration got wrong, both ways.
        assertSoftly(softly -> {
            softly.assertThat(tables)
                    .extracting(TablePrivileges::tableName)
                    .contains(
                            "audit.audit_record",
                            "planning.plan_revision",
                            "persistence_fixture.ledger",
                            FLYWAY_HISTORY);
            softly.assertThat(tables)
                    .filteredOn(table -> !table.onRows()
                            && !table.isPartition()
                            && !table.tableName().equals(FLYWAY_HISTORY))
                    .extracting(TablePrivileges::tableName)
                    .as("tables that grant app nothing")
                    .isEmpty();
            softly.assertThat(tables)
                    .filteredOn(TablePrivileges::beyondRows)
                    .extracting(TablePrivileges::tableName)
                    .as("tables that grant app more than reading and writing rows")
                    .isEmpty();
            softly.assertThat(tables)
                    .filteredOn(table -> table.tableName().equals(FLYWAY_HISTORY) && table.onRows())
                    .extracting(TablePrivileges::tableName)
                    .as("Flyway's history, which app may not touch")
                    .isEmpty();
            softly.assertThat(sequencesAppMayUse).as("sequences app may use").isEmpty();
            softly.assertThat(functionsOpenToAll)
                    .as("functions every role may execute")
                    .isEmpty();
        });
    }

    /**
     * The tests above try the eight module schemas one by one; this reads every schema, so one a
     * module adds later is covered too: app may use each schema the owner owns, and may create in
     * none.
     */
    @Test
    void GivenEverySchema_WhenAppsPrivilegesOnItAreRead_ThenAppMayUseTheOwnersAndCreateInNone() {
        List<String> ownersSchemasAppCannotUse = owner.sql("""
                        SELECT nspname FROM pg_namespace
                        WHERE pg_get_userbyid(nspowner) = :owner AND NOT has_schema_privilege(:app, oid, 'USAGE')
                        """)
                .param("app", DatabaseRoles.APP)
                .param("owner", DatabaseRoles.OWNER)
                .query(String.class)
                .list();
        // PostgreSQL answers yes for the asking session's own temporary schema, whoever is asked about.
        List<String> schemasAppMayCreateIn = owner.sql("""
                        SELECT nspname FROM pg_namespace
                        WHERE has_schema_privilege(:app, oid, 'CREATE')
                            AND oid <> pg_my_temp_schema() AND NOT pg_is_other_temp_schema(oid)
                        """)
                .param("app", DatabaseRoles.APP)
                .query(String.class)
                .list();

        assertSoftly(softly -> {
            softly.assertThat(ownersSchemasAppCannotUse)
                    .as("the owner's schemas app may not use")
                    .isEmpty();
            softly.assertThat(schemasAppMayCreateIn)
                    .as("schemas app may create in")
                    .isEmpty();
        });
    }

    static List<String> recordsKeptAsWritten() {
        return RECORDS_KEPT_AS_WRITTEN;
    }

    /**
     * The audit trail is append-only (ADR-041) and a published plan's revisions are appended once and
     * never changed (V8, DL §4.4), so the database, not the code, keeps them as written. The guard
     * above allows any of the four row privileges; these tables must hold neither of the two that
     * change a row, not even an {@code UPDATE} on one column, which could still rewrite a record, on
     * the table or on a partition of it, which a statement may name directly. Audit's behaviour tests,
     * a refused {@code UPDATE} and {@code DELETE} and the purge function, come with #180.
     */
    @ParameterizedTest
    @MethodSource("recordsKeptAsWritten")
    void GivenARecordKeptAsWritten_WhenAppsPrivilegesOnItAreRead_ThenItMayNeitherUpdateNorDeleteIt(String table) {
        assertThat(owner.sql("""
                                SELECT bool_or(has_any_column_privilege(:app, member, 'UPDATE')
                                    OR has_table_privilege(:app, member, 'DELETE'))
                                FROM (SELECT CAST(:table AS regclass) AS member
                                    UNION SELECT relid FROM pg_partition_tree(CAST(:table AS regclass))) AS members
                                """)
                        .param("app", DatabaseRoles.APP)
                        .param("table", table)
                        .query(Boolean.class)
                        .single())
                .isFalse();
    }

    /** App may create no schema, and PostgreSQL 15 and later let no ordinary role create in public. */
    @ParameterizedTest
    @ValueSource(strings = {"CREATE SCHEMA made_by_app", "CREATE TABLE public.made_by_app (id integer)"})
    void GivenTheAppRole_WhenItCreatesASchemaOrATableInPublic_ThenItIsRefusedWithInsufficientPrivilege(
            String statement) {
        assertThatThrownBy(() -> jdbc.sql(statement).update())
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        refused -> assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    /** The adapters run on Hibernate's connections, so those, not only the JdbcClient's, must be app's. */
    @Test
    void GivenTheIntegrationTests_WhenTheyConnect_ThenTheDatasourceUserIsApp() {
        assertThat(jdbc.sql("SELECT current_user").query(String.class).single()).isEqualTo(DatabaseRoles.APP);
        assertThat(transactions.inTransaction(() -> entityManager
                        .createNativeQuery("SELECT CAST(current_user AS text)")
                        .getSingleResult()))
                .isEqualTo(DatabaseRoles.APP);
    }

    /** The owner's pool binds the core's pool settings, so Flyway in the tests runs as the core does. */
    @Test
    void GivenTheOwnersPool_WhenItsSessionIsAsked_ThenItRunsAsTheOwnerInUtcWithTheCoresLockTimeout() {
        assertThat(owner.sql("SELECT current_user").query(String.class).single())
                .isEqualTo(DatabaseRoles.OWNER);
        assertThat(owner.sql("SHOW TimeZone").query(String.class).single()).isEqualTo("UTC");
        assertThat(owner.sql("SHOW lock_timeout").query(String.class).single()).isEqualTo("2s");
    }

    /** The fixture's own CHECK refuses this name, and the driver's error detail would quote the row. */
    @Test
    void GivenTheOwnersPool_WhenTheDatabaseRefusesARow_ThenTheErrorQuotesNoValues() {
        assertThatThrownBy(() -> owner.sql("""
                                INSERT INTO persistence_fixture.ledger
                                    (id, version, name, status, booked_for, window_start, window_end, priority)
                                VALUES (?, 0, 'Refused by the database', 'OPEN', DATE '2027-07-13',
                                    TIMESTAMP '2027-07-13 08:00', TIMESTAMP '2027-07-13 12:00', 3)
                                """)
                        .params(UUID.fromString("0190a5e2-0160-7000-8000-000000000003"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(refused -> {
                    for (Throwable cause = refused; cause != null; cause = cause.getCause()) {
                        assertThat(String.valueOf(cause.getMessage())).doesNotContain("Failing row");
                    }
                });
    }

    @Test
    void GivenTheFixtureTables_WhenAppSavesAndReadsThem_ThenTheGrantsOfV0_2Suffice() {
        transactions.inTransaction(() -> {
            jdbc.sql("""
                            INSERT INTO persistence_fixture.ledger
                                (id, version, name, status, booked_for, window_start, window_end, priority)
                            VALUES (?, 0, 'Ledger', 'OPEN', DATE '2027-07-13',
                                TIMESTAMP '2027-07-13 08:00', TIMESTAMP '2027-07-13 12:00', 3)
                            """).params(FIXTURE_LEDGER).update();
            jdbc.sql("""
                            INSERT INTO persistence_fixture.ledger_entry (id, ledger_id, entry_index, amount, attempt)
                            VALUES (?, ?, 0, 10, 1)
                            """).params(FIXTURE_ENTRY, FIXTURE_LEDGER).update();
            jdbc.sql("UPDATE persistence_fixture.ledger SET version = version + 1 WHERE id = ?")
                    .params(FIXTURE_LEDGER)
                    .update();
            jdbc.sql("UPDATE persistence_fixture.ledger_entry SET amount = 12.5 WHERE id = ?")
                    .params(FIXTURE_ENTRY)
                    .update();
        });

        assertThat(jdbc.sql("""
                                SELECT l.version FROM persistence_fixture.ledger l
                                JOIN persistence_fixture.ledger_entry e ON e.ledger_id = l.id
                                WHERE e.id = ? AND e.amount = 12.5
                                """).params(FIXTURE_ENTRY).query(Long.class).single())
                .isEqualTo(1L);
    }

    /**
     * The core's own property files, with the auto-configuration that would run Flyway, connected
     * as app. If the files weren't read, Flyway would run, because Spring Boot turns it on unless a
     * property turns it off. A profile named like the command turns nothing on: only the command
     * itself does, so a run of the whole core given that profile and the owner's credential still
     * migrates nothing. The {@code dev} profile, which the Compose core runs as app, turns nothing on
     * either (ADR-146 decision 4).
     */
    @ParameterizedTest
    @ValueSource(strings = {"default", StandInProfiles.DEV, MigrateCommand.NAME})
    void GivenTheCoresOwnProfiles_WhenItsDatabaseSetupStarts_ThenFlywayDoesNotRun(String profile) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(
                        AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withPropertyValues(
                        "spring.profiles.active=" + profile,
                        "spring.datasource.url=" + postgresContainer.getJdbcUrl(),
                        "spring.datasource.username=" + DatabaseRoles.APP,
                        "spring.datasource.password=" + roles.appPassword())
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(Flyway.class));
    }

    /**
     * The migrate command's own context, built from its class and the setting it adds, as the owner.
     * It proves both that the command turns Flyway on over the property files, and that Flyway runs
     * on the pool the core's settings build, in the core's session, rather than on a connection of
     * its own, which {@code spring.flyway.user} would give it. A no-op strategy keeps it from
     * migrating the shared database, whose history holds the tests' own fixture.
     */
    @Test
    void GivenTheMigrateCommandsContext_WhenItsDatabaseSetupStarts_ThenFlywayRunsOnThePoolInTheCoresSession() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(MigrateCommand.FLYWAY_ON)
                .withUserConfiguration(MigrateCommand.class)
                .withBean(FlywayMigrationStrategy.class, () -> flyway -> {})
                .withPropertyValues(
                        "spring.datasource.url=" + postgresContainer.getJdbcUrl(),
                        "spring.datasource.username=" + DatabaseRoles.OWNER,
                        "spring.datasource.password=" + roles.ownerPassword())
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(Flyway.class);
                    DataSource flywaysDataSource =
                            context.getBean(Flyway.class).getConfiguration().getDataSource();
                    assertThat(flywaysDataSource).isInstanceOf(HikariDataSource.class);
                    JdbcClient asFlyway = JdbcClient.create(flywaysDataSource);
                    assertThat(asFlyway.sql("SELECT current_user")
                                    .query(String.class)
                                    .single())
                            .isEqualTo(DatabaseRoles.OWNER);
                    assertThat(asFlyway.sql("SHOW TimeZone").query(String.class).single())
                            .isEqualTo("UTC");
                    assertThat(asFlyway.sql("SHOW lock_timeout")
                                    .query(String.class)
                                    .single())
                            .isEqualTo("2s");
                });
    }

    /** The command returns with nothing left running, so its process ends, as Compose waits for. */
    @Test
    void GivenThePendingMigrations_WhenTheMigrateStepRuns_ThenItAppliesThemAndExits() {
        DatabaseRoles emptyRoles = new DatabaseRoles();
        try (PostgreSQLContainer empty = TestcontainersConfiguration.postgres(emptyRoles)) {
            empty.start();
            Set<Thread> runningBefore = threadsThatKeepTheJvmRunning();

            runTheMigrateCommand(migrateArguments(empty, emptyRoles.ownerPassword()));

            assertThat(appliedVersions(empty, emptyRoles)).isEqualTo(migrationVersions());
            assertThat(threadsThatKeepTheJvmRunning()).isSubsetOf(runningBefore);
        }
    }

    /** Every deploy runs the step, so on a database already up to date it must change nothing. */
    @Test
    void GivenAMigratedDatabase_WhenTheMigrateStepRunsAgain_ThenItAppliesNothing() {
        DatabaseRoles emptyRoles = new DatabaseRoles();
        try (PostgreSQLContainer empty = TestcontainersConfiguration.postgres(emptyRoles)) {
            empty.start();
            runTheMigrateCommand(migrateArguments(empty, emptyRoles.ownerPassword()));
            long historyBefore = historyRows(empty, emptyRoles);

            runTheMigrateCommand(migrateArguments(empty, emptyRoles.ownerPassword()));

            assertThat(historyRows(empty, emptyRoles)).isEqualTo(historyBefore);
        }
    }

    @Test
    void GivenTheWrongOwnerPassword_WhenTheMigrateStepRuns_ThenItFailsWithoutLoggingThePassword(CapturedOutput output) {
        String wrongPassword = UUID.randomUUID().toString();

        assertThatThrownBy(() -> runTheMigrateCommand(migrateArguments(postgresContainer, wrongPassword)))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        refused -> assertThat(refused.getSQLState()).isEqualTo(INVALID_PASSWORD));
        assertThat(output.getAll())
                .contains("password authentication failed for user")
                .doesNotContain(wrongPassword);
    }

    /**
     * ADR-145 decision 4: CREATE on the database and on {@code public} is all Flyway needs, so the
     * owner is no superuser and owns neither.
     */
    @Test
    void GivenAnEmptyDatabase_WhenTheOwnerRunsFlyway_ThenEveryMigrationAppliesWithoutASuperuser() {
        DatabaseRoles emptyRoles = new DatabaseRoles();
        try (PostgreSQLContainer empty = TestcontainersConfiguration.postgres(emptyRoles)) {
            empty.start();
            DataSource asOwner = asOwner(empty, emptyRoles);

            MigrateResult result = Flyway.configure().dataSource(asOwner).load().migrate();

            assertThat(result.success).isTrue();
            assertThat(appliedVersions(empty, emptyRoles)).isEqualTo(migrationVersions());
            JdbcClient ownerOfEmpty = JdbcClient.create(asOwner);
            assertThat(ownerOfEmpty.sql("""
                                    SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
                                    FROM pg_roles WHERE rolname = current_user
                                    """).query(Boolean.class).single()).isFalse();
            assertThat(ownerOfEmpty
                            .sql("SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = current_database()")
                            .query(String.class)
                            .single())
                    .isNotEqualTo(DatabaseRoles.OWNER);
        }
    }

    /**
     * The script already ran when the container started, so this run would fail on the existing
     * roles if the check let it through; the message is what tells the two apart. An empty value is
     * what Compose passes for a key copied from {@code .env.example} unfilled.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "DISPATCHFLOW_OWNER_PASSWORD, UNSET",
        "DISPATCHFLOW_OWNER_PASSWORD, EMPTY",
        "DISPATCHFLOW_APP_PASSWORD, UNSET",
        "DISPATCHFLOW_APP_PASSWORD, EMPTY"
    })
    void GivenAPasswordMissingFromTheEnvironment_WhenTheInitScriptRuns_ThenItStopsAndNamesIt(
            String variable, Absence absence) throws Exception {
        ExecResult run = runTheInitScript(absence.envArguments(variable));

        assertThat(run.getExitCode()).isNotZero();
        assertThat(run.getStderr()).contains(variable + " is not set");
    }

    /** With one password for both, the core's credential would log in as the owner. */
    @Test
    void GivenOnePasswordForBothRoles_WhenTheInitScriptRuns_ThenItStopsAndSaysTheyMustDiffer() throws Exception {
        ExecResult run = runTheInitScript(List.of("DISPATCHFLOW_APP_PASSWORD=" + roles.ownerPassword()));

        assertThat(run.getExitCode()).isNotZero();
        assertThat(run.getStderr()).contains("must differ");
    }

    /**
     * The script's statements carry both passwords, so it turns off, for its own session, every
     * server setting that would log a statement's text or its parse tree. Here the session starts
     * with all of them on. The script stops at its first {@code CREATE ROLE}, because the roles exist,
     * and a failed statement is the one PostgreSQL logs by default: it writes that statement to its
     * own log alone, never to psql, so the test reads the server's log, up to a line it logs after the
     * script, and psql's own output, which the image's log carries too. Neither statement may show in
     * any form, a parse tree's included, and a failure names no password.
     */
    @Test
    void GivenASessionThatLogsEveryStatement_WhenTheInitScriptRuns_ThenTheServerLogsNoPasswordNotEvenForAFailure()
            throws Exception {
        ToStringConsumer serverLog = new ToStringConsumer();
        WaitingConsumer readSoFar = new WaitingConsumer();
        ExecResult run;
        try (FrameConsumerResultCallback following = followTheServerLog(serverLog.andThen(readSoFar))) {
            run = runTheInitScript(List.of("PGOPTIONS=" + everyStatementLogged()));
            owner.sql(LOG_THAT_THE_CHECK_IS_DONE).update();
            readSoFar.waitUntil(
                    line -> line.getUtf8String().contains(LOG_CHECK_DONE), LOG_WAIT_SECONDS, TimeUnit.SECONDS);
        }
        String psql = run.getStdout() + run.getStderr();
        String logged = serverLog.toUtf8String();

        // The passwords first, so a setting the script stops turning off fails here, then what proves
        // the session logged everything until the script's settings took over.
        assertSoftly(softly -> {
            for (String password : List.of(roles.ownerPassword(), roles.appPassword())) {
                softly.assertThat(logged.contains(password))
                        .withFailMessage("the server's log holds a role's password")
                        .isFalse();
                softly.assertThat(psql.contains(password))
                        .withFailMessage("psql's output holds a role's password")
                        .isFalse();
            }
            softly.assertThat(List.of("CREATE ROLE", ":defname password", "password_missing"))
                    .as("traces of the statements that carry the passwords")
                    .noneMatch(logged::contains);
            softly.assertThat(psql)
                    .withFailMessage("psql's output doesn't say the roles already exist")
                    .contains("already exists");
            softly.assertThat(logged)
                    .withFailMessage("the server's log lacks what the session's settings would have logged")
                    .contains("statement: SET ", "LOG:  parse tree:", "LOG:  rewritten parse tree:", "already exists");
        });
    }

    /**
     * {@link #STATEMENTS_LOGGED}, and every {@code debug_print_} setting this server has, each on,
     * as the init script finds them by name.
     */
    private String everyStatementLogged() {
        return STATEMENTS_LOGGED + " " + owner.sql("""
                                SELECT string_agg('-c ' || name || '=on', ' ') FROM pg_settings
                                WHERE name LIKE 'debug\\_print\\_%'
                                """).query(String.class).single();
    }

    /**
     * A function the owner puts in public, or in a schema named after the superuser, would run with
     * the superuser's rights once the superuser called it by an unqualified name, so the init script
     * keeps the superuser's lookups in this database to the catalog.
     */
    @Test
    void GivenTheMigratedDatabase_WhenTheSuperusersSettingsInItAreRead_ThenItLooksUpNamesInTheCatalogAlone() {
        assertThat(owner.sql("""
                                SELECT array_to_string(s.setconfig, ';') FROM pg_db_role_setting s
                                JOIN pg_database d ON d.oid = s.setdatabase
                                WHERE d.datname = current_database() AND s.setrole = CAST(:superuser AS regrole)
                                """)
                        .param("superuser", postgresContainer.getUsername())
                        .query(String.class)
                        .single())
                .isEqualTo("search_path=pg_catalog, pg_temp");
    }

    /**
     * PostgreSQL lets every role execute a new function unless its owner says otherwise, so V11 has
     * the owner say otherwise: a function app may call is granted to it by the migration that makes
     * it. The probe goes as soon as the test has read its privileges.
     */
    @Test
    void GivenAFunctionTheOwnerCreates_WhenAppsPrivilegeOnItIsRead_ThenAppMayNotExecuteIt() {
        owner.sql("CREATE FUNCTION persistence_fixture.made_by_the_owner() RETURNS integer LANGUAGE sql AS 'SELECT 1'")
                .update();
        try {
            assertThat(owner.sql(
                                    "SELECT has_function_privilege(:app, 'persistence_fixture.made_by_the_owner()', 'EXECUTE')")
                            .param("app", DatabaseRoles.APP)
                            .query(Boolean.class)
                            .single())
                    .isFalse();
        } finally {
            owner.sql("DROP FUNCTION persistence_fixture.made_by_the_owner()").update();
        }
    }

    /** Runs the init script again in the shared container, as its superuser, with these {@code env} arguments. */
    private ExecResult runTheInitScript(List<String> envArguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("env"));
        command.addAll(envArguments);
        command.addAll(List.of(
                "psql",
                "--no-psqlrc",
                "--username",
                postgresContainer.getUsername(),
                "--dbname",
                postgresContainer.getDatabaseName(),
                "--file",
                DatabaseRoles.CONTAINER_INIT_SCRIPT));
        return postgresContainer.execInContainer(command.toArray(String[]::new));
    }

    /**
     * Follows the shared container's server log from this second on, into {@code consumer}, until the
     * returned stream is closed. Docker hands over every line since then, whenever the stream
     * attaches, so none written after this call is missed. The second is the database's: Docker
     * stamps each line by the clock of the machine the container runs on, which on Docker Desktop is a
     * virtual machine whose clock may lag the host's.
     */
    private FrameConsumerResultCallback followTheServerLog(Consumer<OutputFrame> consumer) {
        int since = owner.sql("SELECT floor(extract(epoch FROM clock_timestamp()))::integer")
                .query(Integer.class)
                .single();
        FrameConsumerResultCallback following = new FrameConsumerResultCallback();
        following.addConsumer(OutputFrame.OutputType.STDOUT, consumer);
        following.addConsumer(OutputFrame.OutputType.STDERR, consumer);
        postgresContainer
                .getDockerClient()
                .logContainerCmd(postgresContainer.getContainerId())
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withSince(since)
                .exec(following);
        return following;
    }

    /** How a password is missing from the init script's environment. */
    enum Absence {
        UNSET,
        EMPTY;

        /** The {@code env} arguments that run the script with the variable missing this way. */
        List<String> envArguments(String variable) {
            return switch (this) {
                case UNSET -> List.of("-u", variable);
                case EMPTY -> List.of(variable + "=");
            };
        }
    }

    /**
     * Runs the command as {@code java -jar app.jar migrate} would, through the core's main method.
     * Spring Boot would set this JVM's logging up again for the command's own application, and take
     * the JDK logging bridge down when it closes, under the test context that still runs here; so the
     * command leaves logging to the test context.
     */
    private static void runTheMigrateCommand(String... arguments) {
        String loggingSystem = System.setProperty(LoggingSystem.SYSTEM_PROPERTY, LoggingSystem.NONE);
        try {
            DispatchFlowApplication.main(arguments);
        } finally {
            if (loggingSystem == null) {
                System.clearProperty(LoggingSystem.SYSTEM_PROPERTY);
            } else {
                System.setProperty(LoggingSystem.SYSTEM_PROPERTY, loggingSystem);
            }
        }
    }

    /** The owner's connection, as the migrate step's environment gives it through Spring Boot's keys. */
    private static String[] migrateArguments(PostgreSQLContainer postgres, String ownerPassword) {
        return new String[] {
            MigrateCommand.NAME,
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + DatabaseRoles.OWNER,
            "--spring.datasource.password=" + ownerPassword
        };
    }

    private static DataSource asOwner(PostgreSQLContainer postgres, DatabaseRoles roles) {
        return new SimpleDriverDataSource(
                new Driver(), postgres.getJdbcUrl(), DatabaseRoles.OWNER, roles.ownerPassword());
    }

    private static Set<String> appliedVersions(PostgreSQLContainer postgres, DatabaseRoles roles) {
        return Set.copyOf(JdbcClient.create(asOwner(postgres, roles))
                .sql("SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL")
                .query(String.class)
                .list());
    }

    private static long historyRows(PostgreSQLContainer postgres, DatabaseRoles roles) {
        return JdbcClient.create(asOwner(postgres, roles))
                .sql("SELECT count(*) FROM flyway_schema_history")
                .query(Long.class)
                .single();
    }

    /** Every production migration's version, as Flyway writes it in its history. */
    private static Set<String> migrationVersions() {
        try {
            Resource[] migrations =
                    new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*__*.sql");
            return Arrays.stream(migrations)
                    .map(migration -> {
                        Matcher file = MIGRATION_FILE.matcher(String.valueOf(migration.getFilename()));
                        if (!file.matches()) {
                            throw new AssertionError("not a versioned migration: " + migration.getFilename());
                        }
                        return file.group(1).replace('_', '.');
                    })
                    .collect(Collectors.toSet());
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** The threads that would keep a JVM running once its main method returned. */
    private static Set<Thread> threadsThatKeepTheJvmRunning() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && !thread.isDaemon())
                .collect(Collectors.toSet());
    }

    /**
     * What app may do on one table, as {@code has_table_privilege} and {@code
     * has_any_column_privilege} read it.
     */
    record TablePrivileges(String tableName, boolean isPartition, boolean onRows, boolean beyondRows) {}
}
