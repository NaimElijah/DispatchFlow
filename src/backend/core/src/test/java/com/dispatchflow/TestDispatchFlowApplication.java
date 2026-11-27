package com.dispatchflow;

import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * Runs the core backend locally against a throwaway PostgreSQL container, so no database has to
 * be installed: {@code ./mvnw spring-boot:test-run}. Needs Docker. The core connects as {@code
 * app}, and Flyway migrates the container as the owner first (ADR-145).
 *
 * <p>It runs the {@code dev} profile, so the stand-in session acts as the seeded Dispatcher, naming
 * that account itself ({@link CoreRun}). It turns Flyway on here, never in {@code
 * application-dev.properties}: the Compose core reads that file too, and connects as {@code app},
 * which may not migrate (ADR-146 decision 4). And it pins the loopback address here, so that no
 * exported {@code SERVER_ADDRESS} lets another machine reach a core that signs every call in
 * (ADR-140 decision 4, ADR-148).
 *
 * <p>It runs in Israel's zone, as the tests ({@code argLine}) and the core's image ({@code
 * ENTRYPOINT}) do, so a bug around a clock change shows the same in each (ADR-088, #250). It sets
 * the zone itself, so a run started from the IDE gets it too, and the Maven plugin is left a
 * developer's own JVM options, such as a debugger's (ADR-152).
 */
public class TestDispatchFlowApplication {

    private static final String ISRAEL = "Asia/Jerusalem";

    // Options, which DispatchFlowApplication.main accepts, and the command line outranks every file
    // and the environment, so no exported variable turns Flyway off or widens the address.
    private static final List<String> PINNED = List.of("--spring.flyway.enabled=true", "--server.address=127.0.0.1");

    /**
     * Starts the core backend with a containerised database.
     *
     * @param args command-line arguments, passed on to Spring Boot
     */
    public static void main(String[] args) {
        useIsraelTime();
        SpringApplication.from(DispatchFlowApplication::main)
                .with(TestcontainersConfiguration.class, CoreRun.class)
                .withAdditionalProfiles(StandInProfiles.DEV)
                .run(withPinnedOptions(args));
    }

    /**
     * The stand-in's account for this run, from {@code core-run.properties}, which the main
     * classpath's {@code dev} file leaves out (ADR-147). A {@code @PropertySource} ranks below the
     * environment, so a variable set for one run picks another entry.
     */
    @TestConfiguration(proxyBeanMethods = false)
    @PropertySource("classpath:core-run.properties")
    static class CoreRun {}

    /** Makes Israel's zone the JVM's default, before anything has read it. */
    static void useIsraelTime() {
        TimeZone.setDefault(TimeZone.getTimeZone(ISRAEL));
    }

    /**
     * The command line {@code just core-run} starts with: the given arguments, then the pinned
     * options.
     */
    static String[] withPinnedOptions(String[] args) {
        return Stream.concat(Stream.of(args), PINNED.stream()).toArray(String[]::new);
    }
}
