package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The guard reads a real PostgreSQL session right, and runs when the core starts: a superuser, a
 * role with an attribute that passes the grants, a member of another role, and an owner of any
 * object are each refused (ADR-145, ADR-151). {@code DatabaseSessionGuardTest} covers the decision
 * for a non-English server, which no stock image can start.
 */
@IntegrationTest
class DatabaseSessionGuardIT {

    /** A predefined role that writes every table, and owns nothing PostgreSQL records. */
    private static final String WRITES_EVERY_TABLE = "pg_write_all_data";

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

    /** Ownership counts in any database on the server, not only the core's. */
    @Test
    void GivenARoleThatOwnsAnotherDatabase_WhenTheGuardChecksItsSession_ThenItRefuses() {
        try (TemporaryRole role = new TemporaryRole("").owningADatabase()) {
            assertThatThrownBy(() -> DatabaseSessionGuard.check(role.session()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("owns an object");
        }
    }

    @Test
    void GivenTheTestServer_WhenItsMessagesLanguageIsRead_ThenItIsEnglish() {
        assertThat(DatabaseSessionGuard.readsEnglish(
                        jdbc.sql("SHOW lc_messages").query(String.class).single()))
                .isTrue();
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

    private JdbcClient connectedAs(String role, String password) {
        return JdbcClient.create(
                new SimpleDriverDataSource(new Driver(), postgresContainer.getJdbcUrl(), role, password));
    }

    /**
     * Runs one statement as the superuser, its identifiers and literals quoted by the server's own
     * {@code format}, since DDL takes no bind parameters.
     */
    private void asSuperuser(String template, Object... arguments) {
        JdbcClient superuser = asSuperuser();
        Object[] parameters = new Object[arguments.length + 1];
        parameters[0] = template;
        System.arraycopy(arguments, 0, parameters, 1, arguments.length);
        String statement = superuser
                .sql("SELECT pg_catalog.format(?" + ", ?".repeat(arguments.length) + ")")
                .params(parameters)
                .query(String.class)
                .single();
        superuser.sql(statement).update();
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
            asSuperuser("GRANT %I TO %I", role, name);
            return this;
        }

        TemporaryRole owningADatabase() {
            database = name;
            asSuperuser("CREATE DATABASE %I OWNER %I", database, name);
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
