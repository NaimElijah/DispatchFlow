package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The validator's times for one route of a plan, inside an {@link Evaluation}: the vehicle and
 * driver it judged the route with, and its stops in the order it judged them (FR-23). A plan refuses
 * an evaluation whose vehicle, driver or stops differ from the route after the change, so an
 * evaluation made with the old driver can't pass a hand-over (ADR-101). The validator reads the
 * driver from the roster, so a hand-over's evaluation is made with the vehicle rostered to the new
 * driver (ADR-112). A route the validator couldn't time to its end has no plan, and keeps none
 * once the plan takes these times (ADR-112, ADR-113).
 *
 * @param route the route
 * @param vehicle the vehicle it was judged with
 * @param driver the driver it was judged with
 * @param plan its departure, return and last leg, or empty when the validator couldn't time it
 * @param stops its stops' times, in order
 */
public record RouteTimes(
        RouteId route, VehicleId vehicle, AccountId driver, Optional<RoutePlan> plan, List<StopTiming> stops) {

    /** Checks every part is present, the plan possibly empty, and copies the stops. */
    public RouteTimes {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(plan, "plan");
        stops = List.copyOf(stops);
    }
}
