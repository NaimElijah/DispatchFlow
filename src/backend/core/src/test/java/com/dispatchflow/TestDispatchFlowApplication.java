package com.dispatchflow;

import org.springframework.boot.SpringApplication;

/**
 * Runs the core backend locally against a throwaway PostgreSQL container, so no database has to
 * be installed: {@code ./mvnw spring-boot:test-run}. Needs Docker.
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
                .run(args);
    }
}
