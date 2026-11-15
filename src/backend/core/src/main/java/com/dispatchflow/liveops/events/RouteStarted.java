package com.dispatchflow.liveops.events;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.Objects;

/**
 * A driver started a route, by starting it or by its first stop report (DL §7.2, FR-68, FR-327).
 * Messaging sends *out for delivery* for each stop not yet arrived (FR-351, FR-373).
 *
 * @param eventId the event's ID
 * @param occurredAt when the server recorded the start
 * @param route the route
 * @param driver the driver whose report started it
 * @param startedAt the start's corrected time (BR-114)
 * @param cause what started it
 */
public record RouteStarted(
        EventId eventId, Instant occurredAt, RouteId route, AccountId driver, Instant startedAt, RunStartCause cause)
        implements DomainEvent {

    /** Checks the parts are given. */
    public RouteStarted {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(cause, "cause");
    }
}
