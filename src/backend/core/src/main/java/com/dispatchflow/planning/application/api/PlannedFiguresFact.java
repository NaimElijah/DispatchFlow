package com.dispatchflow.planning.application.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * What a plan's current revision is planned to do, in the measures the baseline comparison uses
 * (FR-38, FR-39, FR-266, FR-341). Reporting translates it into its {@code PlannedFigures} and
 * {@code PlannedOrders}.
 *
 * <p>Built in M5.
 *
 * @param metres the planned road distance over every route, in whole metres, 0 or more
 * @param driverHours the planned working time summed over the routes, zero or more
 * @param orders every order of the plan's day, served or dropped, each once
 */
public record PlannedFiguresFact(long metres, Duration driverHours, List<PlannedOrderFact> orders) {

    /** Checks the driver-hours are present, and copies the orders. */
    public PlannedFiguresFact {
        Objects.requireNonNull(driverHours, "driverHours");
        orders = List.copyOf(orders);
    }
}
