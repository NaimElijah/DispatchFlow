package com.dispatchflow.liveops.events;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.Objects;

/**
 * A failed stop was corrected to delivered after its order was put back, so the put-back's stop is
 * no longer needed (FR-404, ADR-107). Planning's listener takes that stop off its route with the
 * FR-404 cause.
 *
 * @param eventId the event's ID
 * @param occurredAt when the correction was made
 * @param stop the corrected stop
 * @param order its order
 * @param laterStop the put-back's stop, which has no outcome
 * @param laterAttempt the put-back's attempt number
 * @param laterDate the delivery date the put-back's stop is on, so Planning's listener finds the
 *     plan that holds it, as {@code OrderCancelled} names its date
 * @param by the dispatcher who corrected
 */
public record PutBackSuperseded(
        EventId eventId,
        Instant occurredAt,
        StopId stop,
        OrderId order,
        StopId laterStop,
        int laterAttempt,
        DeliveryDate laterDate,
        AccountId by)
        implements DomainEvent {

    /** Checks the parts are given. */
    public PutBackSuperseded {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(laterStop, "laterStop");
        Objects.requireNonNull(laterDate, "laterDate");
        Objects.requireNonNull(by, "by");
    }
}
