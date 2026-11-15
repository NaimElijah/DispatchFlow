package com.dispatchflow.planning.domain.revision;

import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import java.util.Objects;
import java.util.Optional;

/**
 * One stop of a revision, as late reports are judged against it (BR-110, FR-249, ADR-059).
 *
 * @param stop the stop
 * @param order its order
 * @param attempt its attempt
 * @param plannedArrival its planned arrival, if it has planned times; it may pass the date's end
 *     (ADR-112)
 */
public record RevisionStop(StopId stop, OrderId order, Attempt attempt, Optional<SolveTime> plannedArrival) {

    /** Checks every part is present, possibly empty. */
    public RevisionStop {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(plannedArrival, "plannedArrival");
    }
}
