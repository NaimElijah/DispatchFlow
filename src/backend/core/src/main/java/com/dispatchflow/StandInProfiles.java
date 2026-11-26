package com.dispatchflow;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * The condition of everything that stands in for what isn't built yet: the stand-in session that
 * acts for a seeded account until sign-in exists, and each {@code <Port>StandIn} adapter (ADR-121
 * decision 5, ADR-140 decisions 4 and 10, ADR-147). A bean exists under it only when the active
 * profiles are exactly {@link #DEV} or exactly {@link #TEST}, so a profile active only as the
 * default doesn't count, and a deployment that still lacks a real adapter fails at startup instead
 * of running on a stand-in. Mark such a bean {@link StandInOnly}, never {@code @Profile({"dev",
 * "test"})}, which also holds beside any other profile.
 *
 * <p>It sits in the root package, the configuration layer, so {@code web} and every module's
 * adapters may use it, and no application or domain class can ({@code ApplicationRules} rule 1,
 * {@code DomainPurityRules}). The root package depends on nothing below it ({@code ApplicationRules}
 * rule 19), so an adapter that uses this class closes no cycle.
 *
 * <p>It also names the keys every stand-in reads: the shared list of seeded accounts, under {@link
 * #SEEDED_ACCOUNTS}, which the stand-in session and #181's seed both read, and the stand-in's own
 * settings, under {@link #STAND_IN}. And it names what marks a deployed environment, in which the
 * core refuses to boot with a stand-in ({@code web.security}'s guard): {@link #DEPLOYED_MARKER},
 * which every deployed environment sets, and the provider credentials {@link #SMS_CREDENTIAL},
 * {@link #EMAIL_CREDENTIAL} and {@link #PUSH_CREDENTIAL}. Each provider adapter reads its secret
 * from its key, through the constant, so that a credential set by mistake on a developer's machine
 * refuses the stand-in too.
 */
public final class StandInProfiles implements Condition {

    /** The profile of {@code just core-run} and of the Compose core. */
    public static final String DEV = "dev";

    /** The profile of every integration test. */
    public static final String TEST = "test";

    /**
     * The property every deployed environment sets, as the environment variable {@code
     * DISPATCHFLOW_DEPLOYED}, with any value. Email has no credential to detect, because SES
     * authenticates with the VM's role, so this marker is its guard.
     */
    public static final String DEPLOYED_MARKER = "dispatchflow.deployed";

    /** The SMS provider's secret, the one key its adapter reads it from: {@code DISPATCHFLOW_SMS_CREDENTIAL}. */
    public static final String SMS_CREDENTIAL = "dispatchflow.sms.credential";

    /**
     * The email sender's secret, should it ever take one instead of the VM's role: {@code
     * DISPATCHFLOW_EMAIL_CREDENTIAL}.
     */
    public static final String EMAIL_CREDENTIAL = "dispatchflow.email.credential";

    /** The push service's access token, the one key its adapter reads it from: {@code DISPATCHFLOW_PUSH_CREDENTIAL}. */
    public static final String PUSH_CREDENTIAL = "dispatchflow.push.credential";

    /** The three provider credentials, in the order the guard names them. */
    public static final List<String> PROVIDER_CREDENTIALS = List.of(SMS_CREDENTIAL, EMAIL_CREDENTIAL, PUSH_CREDENTIAL);

    /**
     * The prefix of the shared list of seeded accounts, {@code seeded-accounts.properties}: each
     * entry's {@code id}, {@code roles}, {@code address} and {@code language} sit under {@code
     * <prefix>.<entry>}. Each reader binds only the parts it uses.
     */
    public static final String SEEDED_ACCOUNTS = "dispatchflow.seeded-accounts";

    /** The prefix of the stand-in session's own settings, such as the entry it acts as. */
    public static final String STAND_IN = "dispatchflow.stand-in";

    private static final Set<String> STAND_IN_PROFILES = Set.of(DEV, TEST);

    /**
     * Whether the stand-ins may exist: the active profiles are exactly {@link #DEV} or exactly
     * {@link #TEST}.
     *
     * @param environment the environment whose active profiles are read, the default ones left
     *     out
     * @return true when exactly one profile is active and it is one of the two
     */
    public static boolean isActive(Environment environment) {
        String[] active = environment.getActiveProfiles();
        return active.length == 1 && STAND_IN_PROFILES.contains(active[0]);
    }

    /**
     * The keys that mark a deployed environment and are present, by name: never their values,
     * which are secrets.
     *
     * @param environment the environment, its environment variables included
     * @return {@link #DEPLOYED_MARKER} and each of {@link #PROVIDER_CREDENTIALS} that is set, in that
     *     order; empty on a developer's machine
     */
    public static List<String> deployedKeysPresent(Environment environment) {
        return Stream.concat(Stream.of(DEPLOYED_MARKER), PROVIDER_CREDENTIALS.stream())
                .filter(environment::containsProperty)
                .toList();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isActive(context.getEnvironment());
    }
}
