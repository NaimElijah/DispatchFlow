package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.time.Instant;
import java.util.Optional;

/**
 * What a test sees of an {@link Order}, read through its getters: a value that compares by every
 * part, which the entity doesn't. Tests compare an order before and after a command, or with the
 * order they expect, through it.
 *
 * @param id the order
 * @param customer its customer
 * @param date its delivery date
 * @param window its time window
 * @param weight its weight demand
 * @param volume its volume demand
 * @param serviceTime its service time
 * @param priority its priority
 * @param leaveAtDoor whether it may be left at the door
 * @param reference its outside reference, if any
 * @param recipient its own recipient
 * @param note its own delivery note, if any
 * @param access its floor, apartment and entrance code
 * @param attempt its current attempt
 * @param state active or cancelled
 * @param anonymized whether its personal fields were removed
 * @param lastChangedAt when it was last placed, changed, moved, cancelled or put back
 */
public record OrderDescription(
        OrderId id,
        CustomerId customer,
        DeliveryDate date,
        TimeWindow window,
        Weight weight,
        Volume volume,
        ServiceTime serviceTime,
        Priority priority,
        boolean leaveAtDoor,
        Optional<ExternalReference> reference,
        Recipient recipient,
        Optional<DriverNote> note,
        AccessDetails access,
        Attempt attempt,
        Order.State state,
        boolean anonymized,
        Instant lastChangedAt) {

    /**
     * Describes an order.
     *
     * @param order the order
     * @return what its getters give
     */
    public static OrderDescription of(Order order) {
        return new OrderDescription(
                order.id(),
                order.customer(),
                order.date(),
                order.window(),
                order.weight(),
                order.volume(),
                order.serviceTime(),
                order.priority(),
                order.leaveAtDoor(),
                order.reference(),
                order.recipient(),
                order.note(),
                order.access(),
                order.attempt(),
                order.state(),
                order.isAnonymized(),
                order.lastChangedAt());
    }
}
