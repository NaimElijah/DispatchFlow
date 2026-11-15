package com.dispatchflow.planning.events;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A published plan was cancelled for a day that does not run, so its drivers are alerted (FR-336,
 * FR-337, ADR-101).
 *
 * @param eventId the event's ID
 * @param occurredAt when it was cancelled
 * @param plan the plan
 * @param date its delivery date
 * @param routes every route the plan had, in the plan's order
 */
public record PlanCancelled(EventId eventId, Instant occurredAt, PlanId plan, DeliveryDate date, List<RouteId> routes)
        implements DomainEvent {

    /** Checks every part is present, and copies the routes. */
    public PlanCancelled {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
        routes = List.copyOf(routes);
    }
}
