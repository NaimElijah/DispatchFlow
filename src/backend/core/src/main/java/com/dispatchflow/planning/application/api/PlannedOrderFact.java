package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.OrderId;
import java.util.Objects;

/**
 * One order of a plan's day and what the plan does with it (FR-266). Reporting builds its {@code
 * PlannedOrder} from it and the order's window, which Customers &amp; Orders' {@code
 * OrdersForDate} gives.
 *
 * <p>Built in M5.
 *
 * @param order the order
 * @param service served at a planned start, or dropped
 */
public record PlannedOrderFact(OrderId order, PlannedServiceFact service) {

    /** Checks both parts are present. */
    public PlannedOrderFact {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(service, "service");
    }
}
