package com.dispatchflow.access.domain;

import java.util.Objects;

/**
 * The hash of a refresh token or a credential link's token, which is all that is stored of either
 * (NFR-91, NFR-211). The hashing adapter makes it; the domain only compares it.
 *
 * <p>{@link #toString()} hides it, so it can't reach a log (NFR-211).
 *
 * @param value the encoded hash, in the adapter's own format
 */
public record TokenHash(String value) {

    /** Checks the hash is present. */
    public TokenHash {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A form that hides the hash.
     *
     * @return {@code TokenHash[redacted]}
     */
    @Override
    public String toString() {
        return "TokenHash[redacted]";
    }
}
