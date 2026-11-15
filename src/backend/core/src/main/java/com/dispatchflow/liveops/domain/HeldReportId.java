package com.dispatchflow.liveops.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Names a report held for a dispatcher's decision (BR-132). A local ID: only its {@link
 * com.dispatchflow.liveops.domain.delivery.Delivery} and the dispatcher's screen name it, so it is
 * not in the shared kernel.
 *
 * @param value the UUID
 */
public record HeldReportId(UUID value) {

    /** Checks the UUID is present. */
    public HeldReportId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static HeldReportId of(UUID value) {
        return new HeldReportId(value);
    }
}
