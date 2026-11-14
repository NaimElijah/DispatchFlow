package com.dispatchflow.access.domain;

import com.dispatchflow.shared.StoredStateInvalid;

/**
 * A session's own revocation counter: giving the password for a step-up moves it, and the
 * session's older tokens are refused (NFR-214, NFR-227).
 *
 * <p>It only grows, and a token carrying an older one is refused at once, by an exact integer
 * comparison (NFR-227; DL §4.1, §16). It is its own type, so an account's generation can't be passed for it.
 *
 * @param value the count of revocations, zero before the first
 */
public record SessionGeneration(long value) {

    /** The generation of a session that has not given its password since it opened. */
    public static final SessionGeneration INITIAL = new SessionGeneration(0);

    /**
     * Checks a stored or signed generation.
     *
     * @throws StoredStateInvalid when {@code value} is negative, which no revocation produces
     */
    public SessionGeneration {
        if (value < 0) {
            throw new StoredStateInvalid(SessionGeneration.class, "a generation is never below zero");
        }
    }

    /**
     * Rebuilds a stored generation, or reads one a token carries.
     *
     * @param value the count
     * @return the generation
     * @throws StoredStateInvalid when {@code value} is negative
     */
    public static SessionGeneration of(long value) {
        return new SessionGeneration(value);
    }

    /**
     * The generation after one more revocation.
     *
     * @return this generation plus one
     */
    public SessionGeneration next() {
        return new SessionGeneration(value + 1);
    }

    /**
     * Whether this generation came before another, so a token issued under it is refused.
     *
     * @param other the generation now in force
     * @return {@code true} when this one is lower
     */
    public boolean isOlderThan(SessionGeneration other) {
        return value < other.value;
    }
}
