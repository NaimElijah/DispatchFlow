package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.planning.events.WithdrawalCause;
import java.util.Objects;

/**
 * A route a re-optimization took off its vehicle after the vehicle had served stops (FR-267). It
 * keeps the stops it made, never returns (FR-268), and doesn't count under BR-39.
 *
 * @param cause why the vehicle was withdrawn (FR-271)
 */
public record Withdrawal(WithdrawalCause cause) {

    /** Checks the cause is present. */
    public Withdrawal {
        Objects.requireNonNull(cause, "cause");
    }
}
