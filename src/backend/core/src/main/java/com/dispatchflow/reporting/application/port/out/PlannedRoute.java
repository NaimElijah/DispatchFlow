package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.baseline.Distance;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One route of a plan as its latest revision has it, as Planning's published view gives it (FR-38,
 * FR-106, ADR-041, ADR-109): what the daily summary's per-route line and the planned-against-actual
 * arrivals are built from. It holds IDs, times and figures only, never personal data.
 *
 * <p>Built in M5.
 *
 * @param route the route
 * @param vehicle its vehicle
 * @param driver its driver in the plan's latest revision
 * @param plannedTravel the route's planned distance and driving time, or empty when the validator
 *     couldn't time it (ADR-112)
 * @param stops its stops, in planned order
 */
public record PlannedRoute(
        RouteId route, VehicleId vehicle, AccountId driver, Optional<Travel> plannedTravel, List<PlannedStop> stops) {

    /** Checks every part is present, possibly empty, and copies the stops. */
    public PlannedRoute {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(plannedTravel, "plannedTravel");
        stops = List.copyOf(stops);
    }

    /**
     * A route's planned distance and driving time (FR-38, FR-341).
     *
     * <p>Built in M5.
     *
     * @param distance the planned road distance
     * @param drivingTime the planned time driven, zero or more
     */
    public record Travel(Distance distance, Duration drivingTime) {

        /** Checks both parts are present. */
        public Travel {
            Objects.requireNonNull(distance, "distance");
            Objects.requireNonNull(drivingTime, "drivingTime");
        }
    }

    /**
     * One stop of the route, with its planned arrival (FR-106).
     *
     * <p>Built in M5.
     *
     * @param stop the stop
     * @param order its order
     * @param plannedArrival when the plan has the vehicle reach it, or empty when it has no planned
     *     time: a stop that joined through a late fact, or that the validator couldn't time (DL §8,
     *     ADR-112)
     */
    public record PlannedStop(StopId stop, OrderId order, Optional<Instant> plannedArrival) {

        /** Checks every part is present, possibly empty. */
        public PlannedStop {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(plannedArrival, "plannedArrival");
        }
    }
}
