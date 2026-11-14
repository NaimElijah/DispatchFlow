package com.dispatchflow.orders.events;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.OrderId;
import java.time.Instant;
import java.util.Objects;

/**
 * An order was cancelled (FR-58, DL §7.2). Planning takes its stop off the plan of its date
 * (FR-207, FR-316), and Messaging tells the recipient (FR-354). Live Operations judges a late
 * report against {@link #occurredAt()} (BR-88, BR-130).
 *
 * @param eventId the event's ID
 * @param occurredAt when the order was cancelled
 * @param order the order
 * @param date its delivery date
 * @param attempt the attempt that was current, as a number from 1
 */
public record OrderCancelled(EventId eventId, Instant occurredAt, OrderId order, DeliveryDate date, int attempt)
        implements DomainEvent {

    /** Checks the parts are given. */
    public OrderCancelled {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(date, "date");
    }
}
