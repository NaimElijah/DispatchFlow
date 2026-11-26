package com.dispatchflow;

import org.springframework.boot.SpringApplication;

/**
 * Runs the core backend locally against a throwaway PostgreSQL container, so no database has to
 * be installed: {@code ./mvnw spring-boot:test-run}. Needs Docker. The core connects as {@code
 * app}, and Flyway migrates the container as the owner first (ADR-145).
 *
 * <p>Whichever of #165 and #170 adds the {@code dev} profile runs it here, and turns Flyway on here
 * in place of {@code throwaway-database}, never in {@code application-dev.properties}: the Compose
 * core reads that file too, and connects as {@code app}, which may not migrate (ADR-146 decision 4).
 */
public class TestDispatchFlowApplication {

    /**
     * Starts the core backend with a containerised database.
     *
     * @param args command-line arguments, passed on to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.from(DispatchFlowApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles(TestcontainersConfiguration.THROWAWAY_DATABASE)
                .run(args);
    }
}
