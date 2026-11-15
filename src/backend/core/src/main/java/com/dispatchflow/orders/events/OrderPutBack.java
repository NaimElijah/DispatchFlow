package com.dispatchflow.orders.events;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.OrderId;
import java.time.Instant;
import java.util.Objects;

/**
 * A failed or dropped order was put back onto another date, as its next attempt (FR-75, FR-258,
 * ADR-115). Planning takes the attempt's drop off the old date's plan, as it does for a date change
 * (FR-316), so that plan can still be published; a failed attempt's stop keeps its recorded outcome
 * (FR-258). A put-back on the order's own date raises no event.
 *
 * @param eventId the event's ID
 * @param occurredAt when the order was put back
 * @param order the order
 * @param from the date it left, whose plan held the attempt
 * @param to the date it is on now
 * @param attempt the attempt that failed or was dropped, as a number from 1; the order's current
 *     attempt is the next one
 */
public record OrderPutBack(
        EventId eventId, Instant occurredAt, OrderId order, DeliveryDate from, DeliveryDate to, int attempt)
        implements DomainEvent {

    /** Checks the parts are given. */
    public OrderPutBack {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
