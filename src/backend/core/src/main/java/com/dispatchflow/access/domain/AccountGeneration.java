package com.dispatchflow.access.domain;

import com.dispatchflow.shared.StoredStateInvalid;

/**
 * An account's revocation counter: every revocation of its sessions moves it, and every token
 * and session issued before is refused (NFR-90, NFR-177).
 *
 * <p>It only grows, and a token carrying an older one is refused at once, by an exact integer
 * comparison (NFR-177; DL §4.1, §16). It is its own type, so a session's generation can't be passed for it.
 *
 * @param value the count of revocations, zero before the first
 */
public record AccountGeneration(long value) {

    /** The generation of an account no revocation has touched. */
    public static final AccountGeneration INITIAL = new AccountGeneration(0);

    /**
     * Checks a stored or signed generation.
     *
     * @throws StoredStateInvalid when {@code value} is negative, which no revocation produces
     */
    public AccountGeneration {
        if (value < 0) {
            throw new StoredStateInvalid(AccountGeneration.class, "a generation is never below zero");
        }
    }

    /**
     * Rebuilds a stored generation, or reads one a token carries.
     *
     * @param value the count
     * @return the generation
     * @throws StoredStateInvalid when {@code value} is negative
     */
    public static AccountGeneration of(long value) {
        return new AccountGeneration(value);
    }

    /**
     * The generation after one more revocation.
     *
     * @return this generation plus one
     */
    public AccountGeneration next() {
        return new AccountGeneration(value + 1);
    }

    /**
     * Whether this generation came before another, so a token issued under it is refused.
     *
     * @param other the generation now in force
     * @return {@code true} when this one is lower
     */
    public boolean isOlderThan(AccountGeneration other) {
        return value < other.value;
    }
}
