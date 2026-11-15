package com.dispatchflow.orders.events;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.OrderId;
import java.time.Instant;
import java.util.Objects;

/**
 * An order moved to another date (FR-57, FR-315, DL §7.2). Planning takes its stop off the old
 * date's plan at once (FR-315, FR-316), and Messaging tells the recipient (FR-354).
 *
 * @param eventId the event's ID
 * @param occurredAt when the order was moved
 * @param order the order
 * @param from the date it left
 * @param to the date it is on now
 * @param attempt the current attempt, as a number from 1; a reschedule doesn't start a new one
 */
public record OrderRescheduled(
        EventId eventId, Instant occurredAt, OrderId order, DeliveryDate from, DeliveryDate to, int attempt)
        implements DomainEvent {

    /** Checks the parts are given. */
    public OrderRescheduled {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
