package com.dispatchflow;

import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * The {@code migrate} command: the one-shot step that applies the pending Flyway migrations before
 * the core starts, then exits (ADR-041, ADR-145). It runs from the core's own image, as {@code java
 * -jar app.jar migrate}, connected as {@code dispatchflow_owner} through the usual {@code
 * SPRING_DATASOURCE_*} keys, which only the step's own environment sets to the owner.
 *
 * <p>Its context starts the data source and Flyway and nothing else, so the owner's credential never
 * reaches the application's own beans: no web server, job or adapter. Flyway runs on the same pool
 * settings as the core, from {@code application.properties}: UTC, no server error detail, and the
 * 2-second lock timeout a migration waits at most (ADR-136, ADR-145). The class is no {@code
 * @Configuration}, so the core's component scan never picks it up.
 */
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
public final class MigrateCommand {

    /** The first command-line argument that runs this command instead of the core. */
    public static final String NAME = "migrate";

    /**
     * Turns Flyway on in the command's context alone, above every property file, which keep it off.
     * No profile or property file does it, so no run of the whole core, a Compose service given the
     * owner's credential but not this command among them, can turn Flyway on by naming one.
     */
    static final ApplicationContextInitializer<ConfigurableApplicationContext> FLYWAY_ON =
            context -> context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource(NAME, Map.of("spring.flyway.enabled", "true")));

    private MigrateCommand() {}

    /**
     * Applies every pending migration, then closes the context and its connections. Flyway runs each
     * transactional migration in a transaction of its own, so one that fails leaves the database as
     * the one before it left it; a non-transactional one, such as {@code CREATE INDEX CONCURRENTLY},
     * can leave its work half done and a failed row in Flyway's history (ADR-145 decision 3).
     *
     * @param args Spring Boot's command-line arguments, without the command's name
     * @throws RuntimeException whatever Spring Boot or Flyway raised, after Spring Boot has logged it,
     *     so the process exits with a non-zero status
     */
    static void run(String... args) {
        new SpringApplicationBuilder(MigrateCommand.class)
                // Spring Boot would name the core's main class in its startup lines, which reads as
                // though the core itself had started.
                .main(MigrateCommand.class)
                .web(WebApplicationType.NONE)
                .initializers(FLYWAY_ON)
                .run(args)
                .close();
    }
}
