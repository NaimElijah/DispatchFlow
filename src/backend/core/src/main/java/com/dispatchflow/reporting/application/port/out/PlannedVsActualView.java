package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.Distance;
import com.dispatchflow.reporting.domain.DriverHours;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StopId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A date's planned figures beside what actually happened (FR-106, FR-341): distance, driving time
 * and each stop's arrival. It holds IDs, times and figures only, never personal data.
 *
 * <p>Built in M5.
 *
 * @param date the date
 * @param plan the comparison for the date's published or completed plan, or empty when the date
 *     has none (FR-338)
 */
public record PlannedVsActualView(DeliveryDate date, Optional<PlanComparison> plan) {

    /** Checks both parts are present, possibly empty. */
    public PlannedVsActualView {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(plan, "plan");
    }

    /**
     * One plan's planned and actual figures (FR-106).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @param plannedDistance the planned distance over every route (FR-341)
     * @param actualDistance the distance driven, or empty when it was not measured (Q-40)
     * @param plannedDriverHours the planned driver-hours over every route
     * @param actualDrivingTime the time driven, or empty when it was not measured (Q-40)
     * @param stops each stop's planned and actual arrival, in the plan's route order
     */
    public record PlanComparison(
            PlanId plan,
            Distance plannedDistance,
            Optional<Distance> actualDistance,
            DriverHours plannedDriverHours,
            Optional<Duration> actualDrivingTime,
            List<StopArrival> stops) {

        /** Checks every part is present, possibly empty, and copies the stops. */
        public PlanComparison {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(plannedDistance, "plannedDistance");
            Objects.requireNonNull(actualDistance, "actualDistance");
            Objects.requireNonNull(plannedDriverHours, "plannedDriverHours");
            Objects.requireNonNull(actualDrivingTime, "actualDrivingTime");
            stops = List.copyOf(stops);
        }
    }

    /**
     * One stop's planned and actual arrival (FR-106).
     *
     * <p>Built in M5.
     *
     * @param stop the stop
     * @param order its order
     * @param plannedArrival when the plan has the vehicle reach it, or empty when it has no planned
     *     time
     * @param actualArrival when the driver arrived, as corrected for the device's clock (BR-114), or
     *     the delivery time when no arrival was reported (FR-340); empty when neither was reported
     */
    public record StopArrival(
            StopId stop, OrderId order, Optional<Instant> plannedArrival, Optional<Instant> actualArrival) {

        /** Checks every part is present, possibly empty. */
        public StopArrival {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(plannedArrival, "plannedArrival");
            Objects.requireNonNull(actualArrival, "actualArrival");
        }
    }
}
