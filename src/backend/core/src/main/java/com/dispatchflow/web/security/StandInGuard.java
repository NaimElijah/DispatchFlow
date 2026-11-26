package com.dispatchflow.web.security;

import com.dispatchflow.StandInProfiles;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to boot where a stand-in must never run (ADR-121 decision 5, ADR-140 decision 4,
 * ADR-147). It exists under every profile, unlike the stand-ins it guards, and checks once, at
 * startup, even with {@code spring.main.lazy-initialization} on, which would otherwise create it
 * only when another bean asked for it, and none does:
 *
 * <ul>
 *   <li>the stand-in session's configuration, any property under {@value #STAND_IN} or {@value
 *       SeededAccounts#PREFIX}, is present while the active profiles are anything but exactly
 *       {@code dev} or exactly {@code test}: {@code dev} beside {@code e2e}, {@code test} beside
 *       {@code dev}, or a stand-in property exported where no such profile is active;
 *   <li>a stand-in may run, and a deployed environment is detected: {@link
 *       StandInProfiles#DEPLOYED_MARKER} or a provider credential is set.
 * </ul>
 *
 * <p>Its messages name the profiles and the keys, never a key's value. {@code StandInGuardTest}
 * proves each refusal.
 */
@Component
@Lazy(false)
class StandInGuard {

    /** The prefix of the stand-in session's own properties. */
    static final String STAND_IN = StandInProfiles.STAND_IN;

    /**
     * Checks the environment once, at startup.
     *
     * @param environment the environment the core boots with
     * @throws IllegalStateException naming the profiles or the keys, when either refusal applies
     */
    StandInGuard(Environment environment) {
        check(environment);
    }

    /**
     * Refuses an environment in which a stand-in must not run. The guard runs it at startup, and the
     * stand-in session runs it again as it is made, so no setting that skips the guard, such as a
     * lazy bean factory, lets the session exist unchecked.
     *
     * @param environment the environment the core boots with
     * @throws IllegalStateException naming the profiles or the keys, when either refusal applies
     */
    static void check(Environment environment) {
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        boolean standInMayRun = StandInProfiles.isActive(environment);
        if (!standInMayRun && (isSet(environment, STAND_IN) || isSet(environment, SeededAccounts.PREFIX))) {
            throw new IllegalStateException("The stand-in session's configuration is set, but the active profiles are "
                    + active + ": it runs only when exactly " + StandInProfiles.DEV + " or exactly "
                    + StandInProfiles.TEST + " is active");
        }
        List<String> deployed = StandInProfiles.deployedKeysPresent(environment);
        if (standInMayRun && !deployed.isEmpty()) {
            throw new IllegalStateException("A stand-in would run in a deployed environment, because "
                    + String.join(", ", deployed) + " is set under the profile " + active);
        }
    }

    private static boolean isSet(Environment environment, String prefix) {
        ConfigurationPropertyName name = ConfigurationPropertyName.of(prefix);
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (source.containsDescendantOf(name) == ConfigurationPropertyState.PRESENT) {
                return true;
            }
        }
        return false;
    }
}
