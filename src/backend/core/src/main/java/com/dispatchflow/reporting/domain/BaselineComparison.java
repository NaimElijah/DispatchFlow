package com.dispatchflow.reporting.domain;

import com.dispatchflow.shared.VehicleId;
import java.util.List;
import java.util.Objects;

/**
 * A plan set against its baseline (FR-39): on every measure, on kilometres and on-time only after a
 * vehicle was withdrawn (FR-269), or not at all for a cancelled plan (FR-338).
 */
public sealed interface BaselineComparison {

    /**
     * The plan and its baseline on kilometres, driver-hours and planned on-time (FR-39).
     *
     * @param planDistance the plan's planned distance
     * @param baselineDistance the baseline's distance
     * @param planDriverHours the plan's planned driver-hours
     * @param baselineDriverHours the baseline's driver-hours
     * @param planOnTime the plan's planned on-time rate
     * @param baselineOnTime the baseline's planned on-time rate
     */
    record AllMeasures(
            Distance planDistance,
            Distance baselineDistance,
            DriverHours planDriverHours,
            DriverHours baselineDriverHours,
            PlannedOnTimeRate planOnTime,
            PlannedOnTimeRate baselineOnTime)
            implements BaselineComparison {

        /** Checks every part is present. */
        public AllMeasures {
            Objects.requireNonNull(planDistance, "planDistance");
            Objects.requireNonNull(baselineDistance, "baselineDistance");
            Objects.requireNonNull(planDriverHours, "planDriverHours");
            Objects.requireNonNull(baselineDriverHours, "baselineDriverHours");
            Objects.requireNonNull(planOnTime, "planOnTime");
            Objects.requireNonNull(baselineOnTime, "baselineOnTime");
        }
    }

    /**
     * A day with a vehicle withdrawn mid-route, on kilometres and planned on-time only, because the
     * withdrawn routes' driver-hours are unknown (FR-268, FR-269).
     *
     * @param planDistance the plan's planned distance
     * @param baselineDistance the baseline's distance
     * @param planOnTime the plan's planned on-time rate
     * @param baselineOnTime the baseline's planned on-time rate
     * @param withdrawn the vehicles withdrawn, which the comparison names (FR-269)
     */
    record KilometresAndOnTime(
            Distance planDistance,
            Distance baselineDistance,
            PlannedOnTimeRate planOnTime,
            PlannedOnTimeRate baselineOnTime,
            List<VehicleId> withdrawn)
            implements BaselineComparison {

        /** Checks every part is present, and copies the vehicles. */
        public KilometresAndOnTime {
            Objects.requireNonNull(planDistance, "planDistance");
            Objects.requireNonNull(baselineDistance, "baselineDistance");
            Objects.requireNonNull(planOnTime, "planOnTime");
            Objects.requireNonNull(baselineOnTime, "baselineOnTime");
            withdrawn = List.copyOf(withdrawn);
        }
    }

    /** The plan was cancelled, so it is not compared (FR-338). */
    record NotCompared() implements BaselineComparison {}
}
