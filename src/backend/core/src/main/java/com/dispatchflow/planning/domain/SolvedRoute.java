package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One route of a solution, before the plan gives it a {@code RouteId} and a route index (FR-17).
 * The validator accepted it, so each of its stops is timed; a route with no stops is not driven,
 * and has no plan (ADR-112, ADR-113).
 *
 * @param vehicle the vehicle that drives it
 * @param driver the driver
 * @param plan its departure, return and last leg, or empty for a route with no stops
 * @param stops its stops, in order
 */
public record SolvedRoute(VehicleId vehicle, AccountId driver, Optional<RoutePlan> plan, List<SolvedStop> stops) {

    /** Checks every part is present, the plan possibly empty, and copies the stops. */
    public SolvedRoute {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(plan, "plan");
        stops = List.copyOf(stops);
    }
}
