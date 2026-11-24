package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.Objects;

/**
 * The stop that served an order's attempt on a date, and the route that last held it, read from
 * the stored revisions, so the answer stands after Planning took the stop off (ADR-059). Recipient
 * Messaging reads it when an order is cancelled or rescheduled, because Planning hears the same
 * events and may have taken the stop off first (FR-354).
 *
 * <p>To Be Implemented.
 *
 * @param plan the plan that held the stop
 * @param stop the stop
 * @param route the route that last held it
 */
public record OrderStopFact(PlanId plan, StopId stop, RouteId route) {

    /** Checks every part is present. */
    public OrderStopFact {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(route, "route");
    }
}
