package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The guard reads a real PostgreSQL session right, and runs when the core starts: a superuser, a
 * role with an attribute that passes the grants, a member of another role, an owner of any object,
 * a login that switched role and a server that translates its messages are each refused (ADR-145,
 * ADR-151, ADR-152).
 */
@IntegrationTest
class DatabaseSessionGuardIT {

    /** A predefined role that writes every table, and owns nothing PostgreSQL records. */
    private static final String WRITES_EVERY_TABLE = "pg_write_all_data";

    /** The image's English locale, which a {@code LANGUAGE} variable still translates. */
    private static final String ENGLISH_LOCALE = "en_US.UTF-8";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    @DatabaseOwner
    private JdbcClient owner;

    @Autowired
    private PostgreSQLContainer postgresContainer;

    @Autowired
    private ApplicationContext context;

    @Test
    void GivenTheCoresContext_WhenItStarted_ThenItHoldsTheGuard() {
        assertThat(context.getBeanNamesForType(DatabaseSessionGuard.class)).hasSize(1);
    }

    @Test
    void GivenTheCoreConnectedAsApp_WhenTheGuardChecksItsSession_ThenItPasses() {
        assertThatCode(() -> DatabaseSessionGuard.check(jdbc)).doesNotThrowAnyException();
    }

    /** The migrate step's credential mapped to the core by mistake (ADR-041, ADR-145). */
    @Test
    void GivenTheCoreConnectedAsTheOwner_WhenTheGuardChecksItsSession_ThenItRefusesNamingTheRole() {
        assertThatThrownBy(() -> DatabaseSessionGuard.check(owner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DatabaseRoles.OWNER + " owns an object");
    }

    /** What Spring Boot's Docker Compose support would have connected as (ADR-145, ADR-151). */
    @Test
    void GivenTheCoreConnectedAsTheSuperuser_WhenTheGuardChecksItsSession_ThenItRefusesNamingTheAttribute() {
        assertThatThrownBy(() -> DatabaseSessionGuard.check(asSuperuser()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(postgresContainer.getUsername())
                .hasMessageContaining("SUPERUSER");
    }

    /** Each attribute that ADR-145's roles leave off, and that lets a role pass the grants. */
    @ParameterizedTest
    @ValueSource(strings = {"CREATEDB", "CREATEROLE", "REPLICATION", "BYPASSRLS"})
    void GivenARoleWithAnAttributeThatPassesTheGrants_WhenTheGuardChecksItsSession_ThenItRefusesNamingIt(
            String attribute) {
        try (TemporaryRole role = new TemporaryRole(attribute)) {
            assertThatThrownBy(() -> DatabaseSessionGuard.check(role.session()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(attribute);
        }
    }

    /**
     * A member can {@code SET ROLE} to the owner, and to the superuser or a predefined role, neither
     * of which PostgreSQL records as owning anything: only the membership shows.
     */
    @Test
    void GivenARoleThatIsAMemberOfTheOwner_WhenTheGuardChecksItsSession_ThenItRefusesNamingTheOwner() {
        try (TemporaryRole role = new TemporaryRole("").memberOf(DatabaseRoles.OWNER)) {
            assertThatThrownBy(() -> DatabaseSessionGuard.check(role.session()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("member of " + DatabaseRoles.OWNER);
        }
    }

    /** A member of the database's owner is also, implicitly, a member of pg_database_owner. */
    @Test
    void GivenARoleThatIsAMemberOfTheSuperuser_WhenTheGuardChecksItsSession_ThenItRefuses() {
        try (TemporaryRole role = new TemporaryRole("").memberOf(postgresContainer.getUsername())) {
            assertThatThrownBy(() -> DatabaseSessionGuard.check(role.session()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("is a member of")
                    .hasMessageContaining(postgresContainer.getUsername());
        }
    }

    /** A member that doesn't inherit the owner's rights can still take them with SET ROLE. */
    @Test
    void GivenARoleThatIsAMemberOfTheOwnerWithoutInheriting_WhenTheGuardChecksItsSession_ThenItRefuses() {
        try (TemporaryRole role = new TemporaryRole("").memberOf(DatabaseRoles.OWNER, "WITH INHERIT FALSE")) {
            assertThatThrownBy(() -> DatabaseSessionGuard.check(role.session()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("member of " + DatabaseRoles.OWNER);
        }
    }

    /** A membership through another role counts as one, and both roles are named. */
    @Test
    void GivenARoleThatIsAMemberOfTheOwnerThroughAnother_WhenTheGuardChecksItsSession_ThenItRefusesNamingBoth() {
        try (TemporaryRole middle = new TemporaryRole("").memberOf(DatabaseRoles.OWNER);
                TemporaryRole role = new TemporaryRole("").memberOf(middle.name)) {
            assertThatThrownBy(() -> DatabaseSessionGuard.check(role.session()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(DatabaseRoles.OWNER)
                    .hasMessageContaining(middle.name);
        }
    }

    @Test
    void GivenARoleThatIsAMemberOfAPredefinedRole_WhenTheGuardChecksItsSession_ThenItRefuses() {
        try (TemporaryRole role = new TemporaryRole("").memberOf(WRITES_EVERY_TABLE)) {
            assertThatThrownBy(() -> DatabaseSessionGuard.check(role.session()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("member of " + WRITES_EVERY_TABLE);
        }
    }

    /**
     * A connection that logs in as another role and switches to app, as a connection-init {@code SET
     * ROLE} would, can switch back with {@code RESET ROLE}. One connection holds both statements.
     */
    @Test
    void GivenALoginThatSwitchedToApp_WhenTheGuardChecksItsSession_ThenItRefusesNamingTheLogin() {
        try (TemporaryRole role = new TemporaryRole("").memberOf(DatabaseRoles.APP)) {
            SingleConnectionDataSource connection =
                    new SingleConnectionDataSource(postgresContainer.getJdbcUrl(), role.name, role.password, true);
            try {
                JdbcClient switched = JdbcClient.create(connection);
                switched.sql("SET ROLE " + DatabaseRoles.APP).update();

                assertThatThrownBy(() -> DatabaseSessionGuard.check(switched))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("logs in as " + role.name + " and switches to " + DatabaseRoles.APP);
            } finally {
                connection.destroy();
            }
        }
    }

    /**
     * A superuser's login may take app's identity with {@code SET SESSION AUTHORIZATION}, which
     * changes {@code session_user} as well, and take its own back with {@code RESET SESSION
     * AUTHORIZATION} (ADR-152).
     */
    @Test
    void GivenASuperuserLoginThatTookAppsIdentity_WhenTheGuardChecksItsSession_ThenItRefusesNamingTheLogin() {
        SingleConnectionDataSource connection = new SingleConnectionDataSource(
                postgresContainer.getJdbcUrl(), postgresContainer.getUsername(), postgresContainer.getPassword(), true);
        try {
            JdbcClient switched = JdbcClient.create(connection);
            switched.sql("SET SESSION AUTHORIZATION " + DatabaseRoles.APP).update();

            assertThatThrownBy(() -> DatabaseSessionGuard.check(switched))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(
                            "logs in as " + postgresContainer.getUsername() + " and switches to " + DatabaseRoles.APP);
        } finally {
            connection.destroy();
        }
    }

    /** A database is a shared object; ownership inside another database counts too. */
    @Test
    void GivenARoleThatOwnsAnotherDatabase_WhenTheGuardChecksItsSession_ThenItRefuses() {
        try (TemporaryRole role = new TemporaryRole("").owningADatabase()) {
            assertThatThrownBy(() -> DatabaseSessionGuard.check(role.session()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("owns an object");
        }
    }

    @Test
    void GivenARoleThatOwnsASchemaInAnotherDatabase_WhenTheGuardChecksItsSession_ThenItRefuses() {
        try (TemporaryRole role = new TemporaryRole("").owningASchemaInAnotherDatabase()) {
            assertThatThrownBy(() -> DatabaseSessionGuard.check(role.session()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("owns an object");
        }
    }

    /**
     * An English locale isn't enough: a {@code LANGUAGE} variable in the server's environment
     * translates every message, and the stock image ships the translations (ADR-152). The server's
     * own log is German too, so the container is ready once its port opens.
     */
    @Test
    void GivenAServerWhoseEnvironmentTranslatesAnEnglishLocale_WhenTheGuardChecksAppsSession_ThenItRefuses() {
        DatabaseRoles roles = new DatabaseRoles();
        try (PostgreSQLContainer translating = TestcontainersConfiguration.postgres(roles)
                .withEnv("LANGUAGE", "de")
                .withCommand("postgres", "-c", "fsync=off", "-c", "lc_messages=" + ENGLISH_LOCALE)
                .waitingFor(Wait.forListeningPort())) {
            translating.start();
            JdbcConnectionDetails app = roles.appConnection(translating);
            JdbcClient session = JdbcClient.create(
                    new SimpleDriverDataSource(new Driver(), app.getJdbcUrl(), app.getUsername(), app.getPassword()));

            assertThatThrownBy(() ->
                            session.sql("SELECT 1 / 0").query(Integer.class).single())
                    .rootCause()
                    .hasMessageContaining("Division durch Null");
            assertThatThrownBy(() -> DatabaseSessionGuard.check(session))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("lc_messages is '" + ENGLISH_LOCALE + "'");
        }
    }

    /** The tests' server runs the dev stack's server settings (#250), so it passes as the stack does. */
    @ParameterizedTest
    @CsvSource({
        "lc_messages, C",
        "log_error_verbosity, terse",
        "log_parameter_max_length, 0",
        "log_parameter_max_length_on_error, 0"
    })
    void GivenTheTestServer_WhenOneOfTheStacksSettingsIsRead_ThenItHasTheStacksValue(String setting, String value) {
        assertThat(jdbc.sql("SELECT pg_catalog.current_setting(?)")
                        .param(setting)
                        .query(String.class)
                        .single())
                .isEqualTo(value);
    }

    /**
     * The guard's own annotations: it is made under lazy initialization too, and a refused session
     * stops the context from starting at all.
     */
    @Test
    void GivenLazyInitializationAndTheOwnersConnection_WhenAWebContextStarts_ThenItFailsNamingTheRole() {
        new WebApplicationContextRunner()
                .withInitializer(started ->
                        started.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
                .withBean(JdbcClient.class, () -> owner)
                .withUserConfiguration(DatabaseSessionGuard.class)
                .run(started -> {
                    assertThat(started).hasFailed();
                    assertThat(started.getStartupFailure()).rootCause().hasMessageContaining(DatabaseRoles.OWNER);
                });
    }

    /** A server step runs the core's context with no web server (ADR-131), and is guarded too. */
    @Test
    void GivenAContextWithNoWebServer_WhenItStarts_ThenItHoldsTheGuard() {
        new ApplicationContextRunner()
                .withBean(JdbcClient.class, () -> jdbc)
                .withUserConfiguration(DatabaseSessionGuard.class)
                .run(started -> assertThat(started).hasNotFailed().hasSingleBean(DatabaseSessionGuard.class));
    }

    /** {@code just core-schema-draft} opens no connection, so it has no guard to open one. */
    @Test
    void GivenTheSchemaDraftProfile_WhenAContextStarts_ThenItHasNoGuard() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + DatabaseSessionGuard.SCHEMA_DRAFT)
                .withUserConfiguration(DatabaseSessionGuard.class)
                .run(started -> assertThat(started).hasNotFailed().doesNotHaveBean(DatabaseSessionGuard.class));
    }

    /** The container's own user, which Testcontainers makes a superuser. */
    private JdbcClient asSuperuser() {
        return connectedAs(postgresContainer.getUsername(), postgresContainer.getPassword());
    }

    /** The superuser, connected to another database on the same server. */
    private JdbcClient asSuperuserIn(String database) {
        String url = "jdbc:postgresql://" + postgresContainer.getHost() + ":"
                + postgresContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database;
        return JdbcClient.create(new SimpleDriverDataSource(
                new Driver(), url, postgresContainer.getUsername(), postgresContainer.getPassword()));
    }

    private JdbcClient connectedAs(String role, String password) {
        return JdbcClient.create(
                new SimpleDriverDataSource(new Driver(), postgresContainer.getJdbcUrl(), role, password));
    }

    /** Runs one statement as the superuser on the core's database; see {@link #run}. */
    private void asSuperuser(String template, Object... arguments) {
        run(asSuperuser(), template, arguments);
    }

    /**
     * Runs one statement on a connection, its identifiers and literals quoted by the server's own
     * {@code format}, since DDL takes no bind parameters.
     */
    private static void run(JdbcClient connection, String template, Object... arguments) {
        Object[] parameters = new Object[arguments.length + 1];
        parameters[0] = template;
        System.arraycopy(arguments, 0, parameters, 1, arguments.length);
        String statement = connection
                .sql("SELECT pg_catalog.format(?" + ", ?".repeat(arguments.length) + ")")
                .params(parameters)
                .query(String.class)
                .single();
        connection.sql(statement).update();
    }

    /**
     * A login role made for one test and dropped after it, with the database it owns. Roles are
     * shared by the whole server, so one never outlives its test.
     */
    private final class TemporaryRole implements AutoCloseable {

        private final String name =
                "guard_check_" + UUID.randomUUID().toString().replace("-", "");
        private final String password = UUID.randomUUID().toString();
        private @Nullable String database;

        /**
         * Creates the role.
         *
         * @param attributes a role attribute keyword from this class's tests, or none: the one part
         *     of the statement the server's {@code format} doesn't quote
         */
        TemporaryRole(String attributes) {
            asSuperuser("CREATE ROLE %I LOGIN PASSWORD %L " + attributes, name, password);
        }

        TemporaryRole memberOf(String role) {
            return memberOf(role, "");
        }

        /**
         * Grants the role membership in another.
         *
         * @param role the role it becomes a member of
         * @param options the grant's {@code WITH} options from this class's tests, or none
         */
        TemporaryRole memberOf(String role, String options) {
            asSuperuser("GRANT %I TO %I " + options, role, name);
            return this;
        }

        TemporaryRole owningADatabase() {
            database = name;
            asSuperuser("CREATE DATABASE %I OWNER %I", database, name);
            return this;
        }

        /** A database the superuser owns, holding a schema this role owns. */
        TemporaryRole owningASchemaInAnotherDatabase() {
            database = name;
            asSuperuser("CREATE DATABASE %I", database);
            run(asSuperuserIn(database), "CREATE SCHEMA %I AUTHORIZATION %I", name, name);
            return this;
        }

        JdbcClient session() {
            return connectedAs(name, password);
        }

        @Override
        public void close() {
            if (database != null) {
                asSuperuser("DROP DATABASE %I", database);
            }
            asSuperuser("DROP ROLE %I", name);
        }
    }
}
