package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.util.Objects;
import java.util.Optional;

/**
 * The new values of an order's changeable fields (FR-57, FR-86, FR-87, FR-135). The date changes only
 * through {@link Order#reschedule}, and the outside reference not at all.
 *
 * @param window the window, on the order's date
 * @param weight the weight demand
 * @param volume the volume demand
 * @param serviceTime the service time
 * @param priority the priority
 * @param leaveAtDoor whether it may be left at the door
 * @param recipient its own recipient, or {@link Recipient#NONE}
 * @param note its own delivery note, if any
 * @param access its floor, apartment and entrance code
 */
public record OrderChange(
        TimeWindow window,
        Weight weight,
        Volume volume,
        ServiceTime serviceTime,
        Priority priority,
        boolean leaveAtDoor,
        Recipient recipient,
        Optional<DriverNote> note,
        AccessDetails access) {

    /** Checks every part is given. */
    public OrderChange {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(serviceTime, "serviceTime");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(access, "access");
    }
}
