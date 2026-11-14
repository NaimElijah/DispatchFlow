package com.dispatchflow.shared;

/**
 * The technical counter optimistic locking checks (BR-76, ADR-057). Never shown to a user, and not
 * a plan's revision (DL §17).
 *
 * <p>A new aggregate starts at {@link #NEW}; its first save stores {@code NEW.next()}, and every
 * event a change raises carries the version that change will be saved at.
 *
 * @param value the count of saves, zero before the first
 */
public record Version(long value) {

    /** The version of an aggregate that has never been saved. */
    public static final Version NEW = new Version(0);

    /**
     * Checks a stored version.
     *
     * @throws StoredStateInvalid when {@code value} is negative, which no save produces
     */
    public Version {
        if (value < 0) {
            throw new StoredStateInvalid(Version.class, "a version is never below zero");
        }
    }

    /**
     * Rebuilds a stored version.
     *
     * @param value the stored count
     * @return the version
     * @throws StoredStateInvalid when {@code value} is negative
     */
    public static Version of(long value) {
        return new Version(value);
    }

    /**
     * The version the next save stores.
     *
     * @return this version plus one
     */
    public Version next() {
        return new Version(value + 1);
    }
}
