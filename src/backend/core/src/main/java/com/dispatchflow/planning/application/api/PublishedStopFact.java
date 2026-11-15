package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import java.util.Objects;
import java.util.Optional;

/**
 * One stop of a route in force, in the route's planned order (FR-27, ADR-067). Its window and door
 * flag are not Planning's: Live Operations reads them from Customers &amp; Orders' {@code
 * OrdersForDate} by the order.
 *
 * <p>Built in M5.
 *
 * @param stop the stop
 * @param order its order
 * @param attempt its attempt (FR-258)
 * @param planned its planned times and the leg into it, or empty when it has none: a stop that
 *     joined through a late fact, or that the validator couldn't time (DL §8, ADR-112)
 */
public record PublishedStopFact(StopId stop, OrderId order, Attempt attempt, Optional<PlannedStopTimes> planned) {

    /** Checks every part is present, possibly empty. */
    public PublishedStopFact {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(planned, "planned");
    }
}
