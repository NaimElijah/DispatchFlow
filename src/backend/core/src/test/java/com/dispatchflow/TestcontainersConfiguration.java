package com.dispatchflow;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real PostgreSQL in a container, with DispatchFlow's two database roles (ADR-041, ADR-145), and the
 * test classpath's persistence setup. The core connects as {@code app}, as it does everywhere. Nothing
 * else migrates this throwaway database, so the {@code test} profile and {@link
 * TestDispatchFlowApplication} turn Flyway on in the core's JVM, on a pool of its own connected as
 * {@code dispatchflow_owner}: one of the two exceptions ADR-145 makes to the migrate step, for the
 * tests and {@code just core-run} alone (ADR-146 decision 4).
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(TestPersistenceConfiguration.class)
public class TestcontainersConfiguration {

    /** compose.dev.yaml's tag; {@code just infra-check} fails when the two differ (ADR-152). */
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.6");

    /**
     * The server's command: Testcontainers' own {@code fsync=off}, which a throwaway database can
     * afford, then compose.dev.yaml's server settings (#250), so the tests' server keeps its log as
     * the stack's does and leaves its messages untranslated, which the core refuses to start without
     * (ADR-152).
     */
    private static final String[] POSTGRES_COMMAND = {
        "postgres",
        "-c",
        "fsync=off",
        "-c",
        "log_error_verbosity=terse",
        "-c",
        "log_parameter_max_length=0",
        "-c",
        "log_parameter_max_length_on_error=0",
        "-c",
        "lc_messages=C"
    };

    /** Flyway holds both while it migrates, its main and its migration connection; tests reuse them. */
    private static final int OWNER_POOL_SIZE = 2;

    /**
     * A new, unstarted PostgreSQL container that creates both roles as it first starts, for a test
     * that needs an empty database of its own.
     *
     * @param roles the roles, with their passwords
     * @return the container
     */
    static PostgreSQLContainer postgres(DatabaseRoles roles) {
        return roles.createIn(new PostgreSQLContainer(POSTGRES_IMAGE).withCommand(POSTGRES_COMMAND));
    }

    @Bean
    DatabaseRoles databaseRoles() {
        return new DatabaseRoles();
    }

    @Bean
    PostgreSQLContainer postgresContainer(DatabaseRoles roles) {
        return postgres(roles);
    }

    @Bean
    JdbcConnectionDetails appConnection(PostgreSQLContainer postgresContainer, DatabaseRoles roles) {
        return roles.appConnection(postgresContainer);
    }

    /**
     * The owner's pool, which Flyway runs on. It binds the core's own pool settings, so its sessions
     * run as the core's do: in UTC, without the server's error detail, and with the same lock timeout.
     * Spring Boot binds them after this method returns, so a core pool setting of a name or a size
     * would replace the ones set here; {@code application.properties} sets neither.
     */
    @Bean(defaultCandidate = false)
    @FlywayDataSource
    @DatabaseOwner
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource ownerDataSource(PostgreSQLContainer postgresContainer, DatabaseRoles roles) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgresContainer.getJdbcUrl());
        dataSource.setUsername(DatabaseRoles.OWNER);
        dataSource.setPassword(roles.ownerPassword());
        dataSource.setPoolName("owner");
        dataSource.setMaximumPoolSize(OWNER_POOL_SIZE);
        return dataSource;
    }

    /**
     * Runs a test's SQL as the owner, for the rows the core itself must not write or can't read
     * (ADR-145). It joins no transaction: each statement commits on its own.
     */
    @Bean(defaultCandidate = false)
    @DatabaseOwner
    JdbcClient ownerJdbcClient(@DatabaseOwner DataSource ownerDataSource) {
        return JdbcClient.create(ownerDataSource);
    }
}
