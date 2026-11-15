package com.dispatchflow.reporting.domain;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.TimeWindow;
import java.util.Objects;

/**
 * One order of a plan's day, with its window and what the plan does with it (FR-266). A plan's
 * figures and a baseline's both hold one per order.
 *
 * @param order the order
 * @param window its window; for a leave-at-door order, the preferred one (BR-119, FR-370)
 * @param service served at a planned start, or dropped
 */
public record PlannedOrder(OrderId order, TimeWindow window, PlannedService service) {

    /** Checks every part is present. */
    public PlannedOrder {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(service, "service");
    }
}
