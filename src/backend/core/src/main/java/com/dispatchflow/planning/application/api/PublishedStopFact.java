package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.TimeWindow;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * One stop of a route in force, in the route's planned order (FR-27, ADR-067), with the window,
 * service time and door flag the revision took its order with. Live Operations builds each leg of
 * its projections from it alone, so a stop whose order was since put back, cancelled after it was
 * served, or rescheduled still gets its leg, which a read of the date's uncancelled orders would
 * miss.
 *
 * <p>Planning's domain doesn't hold them yet: {@code PlannedStop} and {@code RevisionStop} keep
 * only the order, the attempt and the planned times. M5 adds the three to each revision's stops,
 * as the revision took them.
 *
 * <p>Built in M5.
 *
 * @param stop the stop
 * @param order its order
 * @param attempt its attempt (FR-258)
 * @param window the order's window the revision was planned with (BR-49)
 * @param serviceTime the order's service time the revision was planned with (BR-04, BR-124), which
 *     a stop with no planned times also needs
 * @param leaveAtDoor whether the order could be left at the door when the revision was planned, so
 *     its window doesn't bind (FR-364, BR-119)
 * @param planned its planned times and the leg into it, or empty when it has none: a stop that
 *     joined through a late fact, or that the validator couldn't time (DL §8, ADR-112)
 */
public record PublishedStopFact(
        StopId stop,
        OrderId order,
        Attempt attempt,
        TimeWindow window,
        Duration serviceTime,
        boolean leaveAtDoor,
        Optional<PlannedStopTimes> planned) {

    /** Checks every part is present, possibly empty. */
    public PublishedStopFact {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(serviceTime, "serviceTime");
        Objects.requireNonNull(planned, "planned");
    }
}
