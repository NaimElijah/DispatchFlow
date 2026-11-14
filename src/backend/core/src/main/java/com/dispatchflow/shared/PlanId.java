package com.dispatchflow.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Names a plan: the intent for one delivery date (DL §4.4).
 *
 * <p>A typed ID, so one kind of ID can't be passed for another (DL §3.3). The UUID's version is not
 * checked: {@code IdGenerator} makes version 7, and the domain only carries it.
 *
 * @param value the UUID
 */
public record PlanId(UUID value) {

    /** Checks the UUID is present. */
    public PlanId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static PlanId of(UUID value) {
        return new PlanId(value);
    }
}
