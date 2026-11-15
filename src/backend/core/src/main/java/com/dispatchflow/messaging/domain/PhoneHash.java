package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * A phone's hash keyed by a secret, which is all an opt-out keeps of the phone (NFR-202). The
 * hashing adapter makes it from the E.164 form, so two spellings of one number hash the same
 * (FR-371, FR-377); the domain only compares it.
 *
 * <p>It still singles out one person's phone, so it is marked {@link PersonalData}: {@link
 * #toString()} hides it, and no error or event carries it.
 *
 * @param value the encoded hash, in the adapter's own format
 */
public record PhoneHash(String value) implements PersonalData {

    /** Checks the hash is present. */
    public PhoneHash {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A form that hides the hash.
     *
     * @return {@code PhoneHash[redacted]}
     */
    @Override
    public String toString() {
        return "PhoneHash[redacted]";
    }
}
