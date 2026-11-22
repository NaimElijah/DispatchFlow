package com.dispatchflow.reporting.domain.baseline;

import com.dispatchflow.reporting.domain.CancelledOrders;
import com.dispatchflow.reporting.domain.PlannedOnTimeRate;
import com.dispatchflow.reporting.domain.PlannedOrders;
import java.util.Objects;

/**
 * What one plan of a day is planned to do, in the measures the baseline comparison uses (FR-39): the
 * distance, the driver-hours and every order's planned service. A published plan's figures come
 * from its current revision, and a baseline's from the nearest-neighbour solve (FR-342).
 *
 * @param distance the planned distance (FR-341)
 * @param driverHours the planned driver-hours
 * @param orders every order of the day, served or dropped
 */
public record PlannedFigures(Distance distance, DriverHours driverHours, PlannedOrders orders) {

    /** Checks every part is present. */
    public PlannedFigures {
        Objects.requireNonNull(distance, "distance");
        Objects.requireNonNull(driverHours, "driverHours");
        Objects.requireNonNull(orders, "orders");
    }

    /**
     * The planned on-time rate of these figures, leaving out the orders cancelled now (FR-266,
     * BR-30).
     *
     * @param cancelled the orders cancelled now
     * @return the rate
     */
    public PlannedOnTimeRate plannedOnTime(CancelledOrders cancelled) {
        return PlannedOnTimeRate.of(orders, cancelled);
    }
}
