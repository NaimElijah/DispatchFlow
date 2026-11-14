package com.dispatchflow.access.domain;

import java.util.Objects;

/**
 * A password as the hashing adapter stored it: salted, from a function built for passwords, and
 * opaque to the domain, which never hashes or compares a password (NFR-36, DL §3.9).
 *
 * <p>{@link #toString()} hides it, so it can't reach a log (NFR-03).
 *
 * @param value the encoded hash, in the adapter's own format
 */
public record PasswordHash(String value) {

    /** Checks the hash is present. */
    public PasswordHash {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A form that hides the hash.
     *
     * @return {@code PasswordHash[redacted]}
     */
    @Override
    public String toString() {
        return "PasswordHash[redacted]";
    }
}
