package com.dispatchflow.liveops.events;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.Objects;

/**
 * A stop got its first outcome (DL §7.2, BR-18). Live Operations' own listener reconciles it with
 * the plan through Planning (DL §8), and Messaging sends *delivered* or *missed you* (FR-355,
 * FR-356, FR-374). A held report raises it only once a dispatcher confirms it (BR-132).
 *
 * @param eventId the event's ID
 * @param occurredAt when the server recorded the outcome
 * @param stop the stop
 * @param order its order
 * @param attempt its attempt number
 * @param route the route that served it: the one the report came from, or the one the dispatcher
 *     recorded for (DL §8)
 * @param outcome delivered or failed
 * @param leftAtDoor whether the outcome is *delivered* and left at the door, which the recipient's
 *     message says (FR-356, FR-366); a correction can't change what the first outcome stated, so the
 *     event carries it
 * @param factTime when it happened: the report's corrected time (BR-114), or the time a dispatcher
 *     recorded it on a driver's behalf
 * @param onBehalf whether a dispatcher recorded it on the driver's behalf (FR-73, FR-257)
 * @param by the driver who reported it, or the dispatcher who recorded it
 */
public record OutcomeRecorded(
        EventId eventId,
        Instant occurredAt,
        StopId stop,
        OrderId order,
        int attempt,
        RouteId route,
        OutcomeKind outcome,
        boolean leftAtDoor,
        Instant factTime,
        boolean onBehalf,
        AccountId by)
        implements DomainEvent {

    /** Checks the parts are given. */
    public OutcomeRecorded {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(factTime, "factTime");
        Objects.requireNonNull(by, "by");
    }
}
