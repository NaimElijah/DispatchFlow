package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.VehicleId;
import java.util.Objects;

/**
 * A vehicle a re-optimization withdraws mid-route, and why (FR-267, FR-271).
 *
 * @param vehicle the vehicle
 * @param cause why
 */
public record VehicleWithdrawal(VehicleId vehicle, WithdrawalCause cause) {

    /** Checks both parts are present. */
    public VehicleWithdrawal {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(cause, "cause");
    }
}
