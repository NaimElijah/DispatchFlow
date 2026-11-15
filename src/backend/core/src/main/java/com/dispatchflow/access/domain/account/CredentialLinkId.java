package com.dispatchflow.access.domain.account;

import java.util.Objects;
import java.util.UUID;

/**
 * Names one credential link of an account (DL §4.1). Local to Access, because no other context
 * names a link.
 *
 * @param value the UUID, which {@code IdGenerator} makes
 */
public record CredentialLinkId(UUID value) {

    /** Checks the UUID is present. */
    public CredentialLinkId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static CredentialLinkId of(UUID value) {
        return new CredentialLinkId(value);
    }
}
