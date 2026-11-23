package com.dispatchflow.shared;

import java.lang.reflect.Field;

/**
 * What saving an aggregate does to the instance, for domain tests that chain commands as separate
 * transactions would (ADR-127 decision 2). A save publishes the pending events and forgets them, as
 * Spring Data does, and stores the next version: 0 for an aggregate never saved, and the stored one
 * plus 1 otherwise, as Hibernate does.
 *
 * <p>Hibernate writes the version field directly, so this does too, by reflection. It is the only
 * test code that reaches into a class's private state.
 *
 * <p>It models a versioned save, and nothing more: not a session's save that only kept or dropped
 * contact times, which leaves the version where it was, and not a reload, which would also reset a
 * root's {@code @Transient} state, such as a plan's pending revision.
 */
public final class AsSaved {

    private static final String VERSION_FIELD = "storedVersion";

    private AsSaved() {}

    /**
     * Saves an aggregate in memory: its events are published and cleared, and its version moves.
     *
     * @param aggregate the aggregate a test saves
     * @param <T> the aggregate's type
     * @return the same instance, now at the version its save stored
     */
    public static <T extends AggregateRoot> T asSaved(T aggregate) {
        SaveEvents.publish(aggregate);
        long stored = aggregate
                .savedVersion()
                .map(Version::next)
                .orElse(Version.of(0))
                .value();
        try {
            Field version = AggregateRoot.class.getDeclaredField(VERSION_FIELD);
            version.setAccessible(true);
            version.set(aggregate, stored);
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("AggregateRoot no longer keeps its version in " + VERSION_FIELD, missing);
        }
        return aggregate;
    }
}
