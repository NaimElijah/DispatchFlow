package com.dispatchflow.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Names an alert a dispatcher must look at (DL §4.5).
 *
 * <p>A typed ID, so one kind of ID can't be passed for another (DL §3.3). The UUID's version is not
 * checked: {@code IdGenerator} makes version 7, and the domain only carries it.
 *
 * @param value the UUID
 */
public record AlertId(UUID value) {

    /** Checks the UUID is present. */
    public AlertId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static AlertId of(UUID value) {
        return new AlertId(value);
    }
}
