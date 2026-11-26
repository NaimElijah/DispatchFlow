package com.dispatchflow;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * Runs the core backend locally against a throwaway PostgreSQL container, so no database has to
 * be installed: {@code ./mvnw spring-boot:test-run}. Needs Docker. The core connects as {@code
 * app}, and Flyway migrates the container as the owner first (ADR-145).
 *
 * <p>It runs the {@code dev} profile, so the stand-in session acts as the seeded Dispatcher on
 * {@code 127.0.0.1}, naming that account itself ({@link CoreRun}), and turns Flyway on here, never in {@code application-dev.properties}: the
 * Compose core reads that file too, and connects as {@code app}, which may not migrate (ADR-146
 * decision 4).
 */
public class TestDispatchFlowApplication {

    private static final String FLYWAY_ON = "--spring.flyway.enabled=true";

    /**
     * Starts the core backend with a containerised database.
     *
     * @param args command-line arguments, passed on to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.from(DispatchFlowApplication::main)
                .with(TestcontainersConfiguration.class, CoreRun.class)
                .withAdditionalProfiles(StandInProfiles.DEV)
                .run(withFlywayOn(args));
    }

    /**
     * The stand-in's account for this run, from {@code core-run.properties}, which the main
     * classpath's {@code dev} file leaves out (ADR-147). A {@code @PropertySource} ranks below the
     * environment, so a variable set for one run picks another entry.
     */
    @TestConfiguration(proxyBeanMethods = false)
    @PropertySource("classpath:core-run.properties")
    static class CoreRun {}

    // An option, which DispatchFlowApplication.main accepts, and the command line outranks every
    // file, so no exported variable turns Flyway off for this run.
    private static String[] withFlywayOn(String[] args) {
        String[] withFlyway = Arrays.copyOf(args, args.length + 1);
        withFlyway[args.length] = FLYWAY_ON;
        return withFlyway;
    }
}
