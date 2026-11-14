package com.dispatchflow.messaging.domain;

import java.util.Objects;

/**
 * The SMS provider's identifier for a message it accepted, by which its delivery reports name the
 * message (NFR-199). It is opaque: the provider adapter checks its form.
 *
 * @param value the provider's identifier
 */
public record ProviderReference(String value) {

    /** Checks the identifier is present. */
    public ProviderReference {
        Objects.requireNonNull(value, "value");
    }
}
