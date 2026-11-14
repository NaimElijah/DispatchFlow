package com.dispatchflow.planning.events;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.List;

/**
 * A plan became the drivers' plan, as revision 1 (DL §7.2, FR-26, FR-152).
 *
 * @param eventId the event's ID
 * @param occurredAt when it was published
 * @param plan the plan
 * @param date its delivery date
 * @param revision its revision number, 1
 * @param routes every route of the plan, in the plan's order
 */
public record PlanPublished(
        EventId eventId, Instant occurredAt, PlanId plan, DeliveryDate date, int revision, List<RouteId> routes)
        implements DomainEvent {

    /** Copies the routes. */
    public PlanPublished {
        routes = List.copyOf(routes);
    }
}
