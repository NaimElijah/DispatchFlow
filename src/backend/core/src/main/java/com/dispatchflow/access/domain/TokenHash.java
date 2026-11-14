package com.dispatchflow.access.domain;

import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * The hash of a refresh token or a credential link's token, which is all that is stored of either
 * (NFR-91, NFR-211). The hashing adapter makes it; the domain only compares it.
 *
 * <p>{@link #toString()} hides it, so it can't reach a log (NFR-211).
 *
 * <p>It is a credential, not personal data, but it is marked {@link PersonalData} so the shape
 * rules keep it out of every error and event field, as they do personal values (ADR-106).
 *
 * @param value the encoded hash, in the adapter's own format
 */
public record TokenHash(String value) implements PersonalData {

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
