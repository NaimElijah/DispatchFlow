package com.dispatchflow.reporting.domain;

import com.dispatchflow.shared.VehicleId;
import java.util.Objects;

/**
 * A vehicle a revision withdrew mid-route, and why (FR-267, FR-271).
 *
 * @param vehicle the withdrawn vehicle
 * @param cause why it was withdrawn, which decides how the baseline's solve treats it
 */
public record Withdrawal(VehicleId vehicle, WithdrawalCause cause) {

    /** Checks both parts are present. */
    public Withdrawal {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(cause, "cause");
    }
}
