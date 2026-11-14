package com.dispatchflow.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Names a stop: an order's place on a route. It stays the same while the stop moves between routes
 * (DL §3.1, §4.4); a delivery and its messages are keyed by it.
 *
 * <p>A typed ID, so one kind of ID can't be passed for another (DL §3.3). The UUID's version is not
 * checked: {@code IdGenerator} makes version 7, and the domain only carries it.
 *
 * @param value the UUID
 */
public record StopId(UUID value) {

    /** Checks the UUID is present. */
    public StopId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static StopId of(UUID value) {
        return new StopId(value);
    }
}
