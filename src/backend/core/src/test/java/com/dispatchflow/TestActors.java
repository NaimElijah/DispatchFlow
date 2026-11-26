package com.dispatchflow;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.ActorRole;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the {@link Actor} of an entry of the shared list of seeded accounts, for a test that calls
 * a use case as that role: {@link #driver()}, {@link #dispatcher()} or {@link #admin()}
 * (ADR-147). The stand-in session acts as the Dispatcher, so a test of a Driver's or an Admin's
 * refusal takes its actor here. Each actor's {@code CallContext} names the entry's account and a
 * session, as {@code Actor}'s constructor demands, with a valid operation and trace ID and no
 * client.
 *
 * <p>The IDs and roles come from {@code seeded-accounts.properties} itself, read from the
 * classpath, so no second copy of them exists.
 */
public final class TestActors {

    /** The session every actor here names. */
    public static final SessionId SESSION = SessionId.of(UUID.fromString("0198c3a0-5d1e-7a02-8a00-000000005e01"));

    /** The operation every actor's call names. */
    public static final String OPERATION = "testCall";

    /** The trace ID every actor's call carries. */
    public static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String LIST = "/seeded-accounts.properties";
    private static final String PREFIX = "dispatchflow.seeded-accounts.";
    private static final Properties SEEDED = read();

    private TestActors() {}

    /**
     * The seeded Driver's actor.
     *
     * @return the actor, which holds only the Driver role
     */
    public static Actor driver() {
        return of("driver");
    }

    /**
     * The seeded Dispatcher's actor, as the stand-in session acts.
     *
     * @return the actor, which holds only the Dispatcher role
     */
    public static Actor dispatcher() {
        return of("dispatcher");
    }

    /**
     * The seeded Admin's actor.
     *
     * @return the actor, which holds only the Admin role
     */
    public static Actor admin() {
        return of("admin");
    }

    /**
     * The actor of any entry on the list, such as one of the demo day's Drivers that #181 adds.
     *
     * @param entry the entry's name, as {@code seeded-accounts.properties} spells it
     * @return the actor
     * @throws IllegalArgumentException when the list has no such entry
     */
    public static Actor of(String entry) {
        AccountId account = AccountId.of(UUID.fromString(required(entry, "id")));
        Set<ActorRole> roles = Arrays.stream(required(entry, "roles").split(","))
                .map(String::strip)
                .map(ActorRole::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        CallContext call =
                new CallContext(Optional.of(account), Optional.of(SESSION), Optional.empty(), OPERATION, TRACE_ID);
        return new Actor(account, roles, call);
    }

    private static String required(String entry, String property) {
        String value = SEEDED.getProperty(PREFIX + entry + "." + property);
        if (value == null) {
            throw new IllegalArgumentException("The seeded accounts have no entry " + entry + " with a " + property);
        }
        return value;
    }

    private static Properties read() {
        Properties properties = new Properties();
        try (InputStream file = TestActors.class.getResourceAsStream(LIST)) {
            if (file == null) {
                throw new IllegalStateException(LIST + " is not on the classpath");
            }
            properties.load(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return properties;
    }
}
