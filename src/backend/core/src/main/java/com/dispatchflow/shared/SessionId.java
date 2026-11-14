package com.dispatchflow.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Names one signed-in device of an account (DL §4.1).
 *
 * <p>A typed ID, so one kind of ID can't be passed for another (DL §3.3). The UUID's version is not
 * checked: {@code IdGenerator} makes version 7, and the domain only carries it.
 *
 * @param value the UUID
 */
public record SessionId(UUID value) {

    /** Checks the UUID is present. */
    public SessionId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static SessionId of(UUID value) {
        return new SessionId(value);
    }
}
