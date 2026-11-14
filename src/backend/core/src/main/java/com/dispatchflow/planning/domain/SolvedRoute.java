package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;

/**
 * One route of a solution, before the plan gives it a {@code RouteId} and a route index (FR-17).
 *
 * @param vehicle the vehicle that drives it
 * @param driver the driver
 * @param plan its departure, return and last leg
 * @param stops its stops, in order
 */
public record SolvedRoute(VehicleId vehicle, AccountId driver, RoutePlan plan, List<SolvedStop> stops) {

    /** Checks every part is present, and copies the stops. */
    public SolvedRoute {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(plan, "plan");
        stops = List.copyOf(stops);
    }
}
