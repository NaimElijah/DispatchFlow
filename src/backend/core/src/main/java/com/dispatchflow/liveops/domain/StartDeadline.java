package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.IsraelDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * When a route not started alerts the dispatcher: the shift's start plus the departure allowance
 * plus 15 minutes (FR-101, FR-67). Planning's settings own the allowance, and a negative one never
 * reaches here (its {@code NegativeDepartureAllowance}).
 *
 * @param at the deadline
 */
public record StartDeadline(Instant at) {

    /** How late a route may start past its planned departure before the dispatcher is alerted. */
    public static final Duration MARGIN = Duration.ofMinutes(15);

    /** Checks the deadline is present. */
    public StartDeadline {
        Objects.requireNonNull(at, "at");
    }

    /**
     * The deadline of a driver's shift.
     *
     * @param shiftStart the shift's start that day
     * @param allowance the departure allowance (FR-67)
     * @return the deadline
     */
    public static StartDeadline of(IsraelDateTime shiftStart, Duration allowance) {
        Objects.requireNonNull(shiftStart, "shiftStart");
        Objects.requireNonNull(allowance, "allowance");
        return new StartDeadline(shiftStart.toInstant().plus(allowance).plus(MARGIN));
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
