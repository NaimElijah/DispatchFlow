package com.dispatchflow.access.domain.session;

import java.util.Objects;
import java.util.UUID;

/**
 * Names one refresh token in a session's chain (DL §4.1). Local to Access, because no other context
 * names a token.
 *
 * @param value the UUID, which {@code IdGenerator} makes
 */
public record RefreshTokenId(UUID value) {

    /** Checks the UUID is present. */
    public RefreshTokenId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static RefreshTokenId of(UUID value) {
        return new RefreshTokenId(value);
    }
}
