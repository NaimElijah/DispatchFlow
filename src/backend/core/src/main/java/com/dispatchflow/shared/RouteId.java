package com.dispatchflow.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Names a route of a plan: one vehicle's and driver's ordered stops (DL §4.4). A route run is keyed by it.
 *
 * <p>A typed ID, so one kind of ID can't be passed for another (DL §3.3). The UUID's version is not
 * checked: {@code IdGenerator} makes version 7, and the domain only carries it.
 *
 * @param value the UUID
 */
public record RouteId(UUID value) {

    /** Checks the UUID is present. */
    public RouteId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static RouteId of(UUID value) {
        return new RouteId(value);
    }
}
