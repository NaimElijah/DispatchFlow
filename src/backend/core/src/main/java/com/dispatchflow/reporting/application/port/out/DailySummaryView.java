package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.Distance;
import com.dispatchflow.reporting.domain.DriverHours;
import com.dispatchflow.reporting.domain.OnTimeRate;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A date's daily summary, in total and per route (FR-38): computed when it is asked for, from the
 * effective outcomes, so a later correction changes it (FR-333, ADR-109). The distance is planned
 * distance (FR-341). It holds IDs, counts and figures only, never personal data.
 *
 * <p>Built in M5.
 *
 * @param date the date
 * @param summary the summary of the date's published or completed plan, or empty when the date
 *     has none; a cancelled plan has no summary (FR-338)
 */
public record DailySummaryView(DeliveryDate date, Optional<Summary> summary) {

    /** Checks both parts are present, possibly empty. */
    public DailySummaryView {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(summary, "summary");
    }

    /**
     * One plan's summary, in total and per route (FR-38).
     *
     * <p>Both travel totals follow the routes' optional values (ADR-112): each is the sum over the
     * routes that have one, since a route with no stops has no times and adds nothing, and empty
     * when a route with stops has none, so a total never leaves out a route that drove.
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @param distance the planned distance over every route (FR-341), or empty when a route with
     *     stops couldn't be timed
     * @param drivingTime the planned driving time over every route (FR-38), or empty when a route
     *     with stops couldn't be timed
     * @param driverHours the planned driver-hours over every route
     * @param stops the plan's stops, by what happened at them
     * @param onTime the on-time percentage of the delivered stops, or unavailable when none was
     *     delivered (FR-264, FR-265, FR-340, FR-370), shown beside the failed stops (FR-339)
     * @param vehiclesUsed how many vehicles have a route with at least one stop
     * @param droppedOrders how many uncancelled orders of the date the plan drops (FR-18, BR-30)
     * @param routes one line per route, in the plan's order
     */
    public record Summary(
            PlanId plan,
            Optional<Distance> distance,
            Optional<Duration> drivingTime,
            DriverHours driverHours,
            StopCounts stops,
            OnTimeRate onTime,
            int vehiclesUsed,
            int droppedOrders,
            List<RouteLine> routes) {

        /** Checks every part is present, possibly empty, and copies the routes. */
        public Summary {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(distance, "distance");
            Objects.requireNonNull(drivingTime, "drivingTime");
            Objects.requireNonNull(driverHours, "driverHours");
            Objects.requireNonNull(stops, "stops");
            Objects.requireNonNull(onTime, "onTime");
            routes = List.copyOf(routes);
        }
    }

    /**
     * One route's line of the summary (FR-38).
     *
     * <p>Built in M5.
     *
     * @param route the route
     * @param vehicle its vehicle
     * @param driver its driver in the plan's current revision
     * @param distance the route's planned distance (FR-341), or empty when the validator couldn't
     *     time the route (ADR-112)
     * @param drivingTime the route's planned driving time, or empty when the validator couldn't time
     *     the route (ADR-112)
     * @param stops the route's stops, by what happened at them
     * @param onTime the route's on-time percentage, or unavailable when none was delivered
     */
    public record RouteLine(
            RouteId route,
            VehicleId vehicle,
            AccountId driver,
            Optional<Distance> distance,
            Optional<Duration> drivingTime,
            StopCounts stops,
            OnTimeRate onTime) {

        /** Checks every part is present, possibly empty. */
        public RouteLine {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(vehicle, "vehicle");
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(distance, "distance");
            Objects.requireNonNull(drivingTime, "drivingTime");
            Objects.requireNonNull(stops, "stops");
            Objects.requireNonNull(onTime, "onTime");
        }
    }

    /**
     * Stops counted by their effective outcome (FR-38, FR-333). A stop of a cancelled order is left
     * out (BR-30).
     *
     * <p>Built in M5.
     *
     * @param stops every stop counted, 0 or more
     * @param delivered the delivered ones
     * @param failed the failed ones, shown beside the on-time percentage (FR-339)
     * @param notReported the ones with no outcome, shown as not reported once the plan completes
     *     (BR-129)
     */
    public record StopCounts(int stops, int delivered, int failed, int notReported) {}
}
