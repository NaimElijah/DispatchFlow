package com.dispatchflow.planning.domain.revision;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;

/**
 * One route of a revision: who drove what, in order (BR-110, ADR-059).
 *
 * @param route the route
 * @param vehicle its vehicle
 * @param driver its driver
 * @param stops its stops, in order
 */
public record RevisionRoute(RouteId route, VehicleId vehicle, AccountId driver, List<RevisionStop> stops) {

    /** Checks every part is present, and copies the stops. */
    public RevisionRoute {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driver, "driver");
        stops = List.copyOf(stops);
    }
}
