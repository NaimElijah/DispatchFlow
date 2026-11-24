package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What one revision of a published plan did to its stops, read from the stored revisions
 * (ADR-059). Recipient Messaging reads it to message a stop that joined a route in progress, and
 * one the revision moved or took off a route (FR-354, FR-372); {@code PlanRevised} names only the
 * routes, because an event carries no copy of the plan (DL §7.3).
 *
 * <p>To Be Implemented.
 *
 * @param plan the plan
 * @param revision the revision's number, 1 or more
 * @param stops every stop the revision added, moved or took off, each once
 */
public record RevisionChangesFact(PlanId plan, int revision, List<StopChange> stops) {

    /** Checks the plan is present, and copies the stops. */
    public RevisionChangesFact {
        Objects.requireNonNull(plan, "plan");
        stops = List.copyOf(stops);
    }

    /**
     * How a revision changed one stop.
     *
     * <p>To Be Implemented.
     */
    public enum Change {
        /** The stop is on a route in this revision and on none in the one before. */
        JOINED,
        /** The stop moved from one route to another. */
        MOVED,
        /** The stop was on a route in the revision before and is on none in this one. */
        LEFT
    }

    /**
     * One stop the revision changed.
     *
     * @param stop the stop
     * @param order its order
     * @param attempt the attempt it serves
     * @param change how the revision changed it
     * @param from the route that held it before, empty for a stop that joined
     * @param to the route that holds it now, empty for a stop that left
     */
    public record StopChange(
            StopId stop, OrderId order, Attempt attempt, Change change, Optional<RouteId> from, Optional<RouteId> to) {

        /** Checks every part is present, possibly empty. */
        public StopChange {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(change, "change");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }
}
