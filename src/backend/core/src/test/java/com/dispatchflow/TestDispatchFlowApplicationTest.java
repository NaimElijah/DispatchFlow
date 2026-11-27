package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.IsraelDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * {@code just core-run}'s pinned options outrank what a developer exported, as Spring Boot ranks
 * the command line above the environment: no {@code SERVER_ADDRESS} widens a core that signs every
 * call in, and none turns Flyway off (ADR-146 decision 4, ADR-148 decision 3). And it runs in
 * Israel's zone, however it is started (ADR-152).
 */
class TestDispatchFlowApplicationTest {

    private static final String SERVER_ADDRESS = "server.address";
    private static final String FLYWAY = "spring.flyway.enabled";

    /** An environment with the given variables exported, and the given command line above them. */
    private static StandardEnvironment environment(Map<String, Object> exported, String... commandLine) {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, exported));
        environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource(commandLine));
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    @Test
    void GivenAnExportedServerAddressAndFlywayOff_WhenCoreRunsOptionsApply_ThenTheLoopbackAndFlywayWin() {
        Map<String, Object> exported = Map.of("SERVER_ADDRESS", "0.0.0.0", "SPRING_FLYWAY_ENABLED", "false");

        StandardEnvironment pinned =
                environment(exported, TestDispatchFlowApplication.withPinnedOptions(new String[0]));

        // Without the pins the exported values would win, so the pins are what this proves.
        assertThat(environment(exported).getProperty(SERVER_ADDRESS)).isEqualTo("0.0.0.0");
        assertThat(pinned.getProperty(SERVER_ADDRESS)).isEqualTo("127.0.0.1");
        assertThat(pinned.getProperty(FLYWAY)).isEqualTo("true");
    }

    @Test
    void GivenArgumentsOfItsOwn_WhenCoreRunsOptionsApply_ThenTheyArePassedOnBeforeThePins() {
        assertThat(TestDispatchFlowApplication.withPinnedOptions(new String[] {"--debug"}))
                .startsWith("--debug")
                .contains("--server.address=127.0.0.1", "--spring.flyway.enabled=true");
    }

    /**
     * The surefire JVM already runs in Israel's zone, so the test starts the JVM's default
     * elsewhere. That {@code main} calls this first is read, not tested: starting {@code main} needs
     * a database.
     */
    @Test
    void GivenAJvmInAnotherZone_WhenTheLocalRunPicksItsZone_ThenItIsIsraels() {
        TimeZone before = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            TestDispatchFlowApplication.useIsraelTime();

            assertThat(ZoneId.systemDefault()).isEqualTo(IsraelDateTime.ZONE);
        } finally {
            TimeZone.setDefault(before);
        }
    }
}
