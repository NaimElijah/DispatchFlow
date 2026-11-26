package com.dispatchflow;

import java.util.UUID;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * DispatchFlow's two database roles in a test's PostgreSQL container (ADR-041, ADR-145): {@value
 * #OWNER}, which runs Flyway and owns every schema and table, and {@value #APP}, which the core
 * connects as. The container creates them with the init scripts development runs, the whole folder,
 * which the pom copies onto the test classpath, so the tests prove those scripts too.
 *
 * <p>Each instance makes its own random passwords, so no password is written anywhere, and none is
 * the same between two runs. Its {@code toString} shows neither.
 */
public final class DatabaseRoles {

    /** The role Flyway runs as, which owns every schema and table. */
    public static final String OWNER = "dispatchflow_owner";

    /** The role the core connects as, which owns nothing. */
    public static final String APP = "app";

    private static final String INIT_FOLDER = "postgres-initdb";
    private static final String CONTAINER_INIT_FOLDER = "/docker-entrypoint-initdb.d";
    /** The roles' script, in the folder the container's entrypoint runs as it first starts. */
    static final String CONTAINER_INIT_SCRIPT = CONTAINER_INIT_FOLDER + "/create-roles.sql";

    private final String ownerPassword = UUID.randomUUID().toString();
    private final String appPassword = UUID.randomUUID().toString();

    /**
     * Has a container run the init scripts while it first starts, creating both roles with this
     * instance's passwords in its environment.
     *
     * @param container a PostgreSQL container that has not started
     * @return the same container, for chaining
     */
    public PostgreSQLContainer createIn(PostgreSQLContainer container) {
        return container
                .withEnv("DISPATCHFLOW_OWNER_PASSWORD", ownerPassword)
                .withEnv("DISPATCHFLOW_APP_PASSWORD", appPassword)
                .withCopyFileToContainer(MountableFile.forClasspathResource(INIT_FOLDER), CONTAINER_INIT_FOLDER);
    }

    /**
     * The core's connection to a container, as {@value #APP}.
     *
     * @param postgres a started container that {@link #createIn} set up
     * @return the connection details Spring Boot builds the core's pool from
     */
    public JdbcConnectionDetails appConnection(PostgreSQLContainer postgres) {
        String url = postgres.getJdbcUrl();
        return new JdbcConnectionDetails() {

            @Override
            public String getJdbcUrl() {
                return url;
            }

            @Override
            public String getUsername() {
                return APP;
            }

            @Override
            public String getPassword() {
                return appPassword;
            }
        };
    }

    /**
     * The owner's password.
     *
     * @return the password {@link #createIn} gave {@value #OWNER}
     */
    public String ownerPassword() {
        return ownerPassword;
    }

    /**
     * App's password.
     *
     * @return the password {@link #createIn} gave {@value #APP}
     */
    public String appPassword() {
        return appPassword;
    }
}
