package com.dispatchflow.liveops.domain.routerun;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * When an unconfirmed route change alerts the dispatcher: 10 minutes after the revision took effect,
 * a starting value (FR-97, FR-157, ADR-107).
 *
 * @param at the deadline
 */
public record ConfirmationDeadline(Instant at) {

    /** How long a driver has to confirm a route change before the dispatcher is alerted. */
    public static final Duration GRACE = Duration.ofMinutes(10);

    /** Checks the deadline is present. */
    public ConfirmationDeadline {
        Objects.requireNonNull(at, "at");
    }

    /**
     * The deadline of a revision.
     *
     * @param revisedAt when the revision took effect
     * @return the deadline, {@link #GRACE} later
     */
    public static ConfirmationDeadline after(Instant revisedAt) {
        return new ConfirmationDeadline(revisedAt.plus(GRACE));
    }

    /**
     * Whether the deadline has come.
     *
     * @param now the time to judge at
     * @return {@code true} at the deadline and after it
     */
    public boolean isPassed(Instant now) {
        return !now.isBefore(at);
    }
}
