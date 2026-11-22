package com.dispatchflow.planning.domain.drop;

import com.dispatchflow.shared.VehicleId;
import java.util.Objects;

/**
 * A vehicle a solve left out, and why, so the dispatcher sees it named (FR-197). The date's
 * exclusions are also the fact of a {@link DropReason.NoVehicleAvailable} drop (ADR-100).
 *
 * @param vehicle the vehicle
 * @param cause why it was left out
 */
public record ExcludedVehicle(VehicleId vehicle, ExclusionCause cause) {

    /** Checks both parts are present. */
    public ExcludedVehicle {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(cause, "cause");
    }
}
