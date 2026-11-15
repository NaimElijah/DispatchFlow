package com.dispatchflow.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Names a request to a recipient to rate a delivery (DL §4.6).
 *
 * <p>A typed ID, so one kind of ID can't be passed for another (DL §3.3). The UUID's version is not
 * checked: {@code IdGenerator} makes version 7, and the domain only carries it.
 *
 * @param value the UUID
 */
public record RatingRequestId(UUID value) {

    /** Checks the UUID is present. */
    public RatingRequestId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static RatingRequestId of(UUID value) {
        return new RatingRequestId(value);
    }
}
