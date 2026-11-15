package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything an {@link Order} holds, as the persistence adapter stores and reloads it. {@link
 * Order#restore} checks the rules that hold at rest.
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
 * @param anonymized whether its personal fields were removed (NFR-257)
 * @param lastChangedAt when it was last placed, changed, moved, cancelled or put back
 * @param version the version it was saved at
 */
public record OrderSnapshot(
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
        Instant lastChangedAt,
        Version version)
        implements PersonalData {

    /** Checks every part is given. */
    public OrderSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(serviceTime, "serviceTime");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(lastChangedAt, "lastChangedAt");
        Objects.requireNonNull(version, "version");
    }

    /**
     * A form that hides the order, because it holds personal data.
     *
     * @return {@code OrderSnapshot[redacted]}
     */
    @Override
    public String toString() {
        return "OrderSnapshot[redacted]";
    }
}
