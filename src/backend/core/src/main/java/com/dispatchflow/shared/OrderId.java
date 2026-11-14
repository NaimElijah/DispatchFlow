package com.dispatchflow.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Names an order: goods to deliver to a customer on a date (DL §4.3).
 *
 * <p>A typed ID, so one kind of ID can't be passed for another (DL §3.3). The UUID's version is not
 * checked: {@code IdGenerator} makes version 7, and the domain only carries it.
 *
 * @param value the UUID
 */
public record OrderId(UUID value) {

    /** Checks the UUID is present. */
    public OrderId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static OrderId of(UUID value) {
        return new OrderId(value);
    }
}
