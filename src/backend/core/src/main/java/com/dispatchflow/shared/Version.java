package com.dispatchflow.shared;

/**
 * The technical counter optimistic locking checks (BR-76, ADR-057). Never shown to a user, and not
 * a plan's revision (DL §17).
 *
 * <p>An aggregate never saved has no version. Its first save stores 0, every save after moves the
 * version by exactly 1, and every event a change raises carries the version that change is saved
 * at (ADR-126).
 *
 * @param value the count of saves after the first, so 0 for an aggregate saved once
 */
public record Version(long value) {

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
