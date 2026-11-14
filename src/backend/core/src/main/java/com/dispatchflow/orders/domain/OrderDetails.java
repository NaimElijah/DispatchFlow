package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.util.Objects;
import java.util.Optional;

/**
 * What a new order is placed with, from the order form (FR-09) or an import row (ADR-086). Its
 * values have checked themselves; {@link Order#place} adds the rules that need the date, the
 * customer's defaults and the date's other orders.
 *
 * @param window its own window, or empty for the customer's default (BR-49)
 * @param weight the weight demand (BR-50)
 * @param volume the volume demand (BR-50)
 * @param serviceTime its own service time, or empty for the customer's default (BR-124)
 * @param priority 1 to 5; the form or the reader gives 3 for a blank (BR-53)
 * @param leaveAtDoor whether it may be left at the door (FR-364)
 * @param reference its outside reference, if any (FR-85)
 * @param recipient its own recipient, or {@link Recipient#NONE} (FR-87)
 * @param note its own delivery note, if any (FR-135)
 * @param access its floor, apartment and entrance code (FR-86)
 */
public record OrderDetails(
        Optional<TimeWindow> window,
        Weight weight,
        Volume volume,
        Optional<ServiceTime> serviceTime,
        Priority priority,
        boolean leaveAtDoor,
        Optional<ExternalReference> reference,
        Recipient recipient,
        Optional<DriverNote> note,
        AccessDetails access) {

    /** Checks every part is given. */
    public OrderDetails {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(serviceTime, "serviceTime");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(access, "access");
    }
}
