package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.orders.domain.ExternalReference;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.util.Objects;
import java.util.Optional;

/**
 * One order as a list shows it (FR-09, FR-76, FR-259): what the dispatcher entered, and the two
 * facts of its own that its status is derived from, its current attempt and whether it is
 * cancelled (BR-33, DL §9.6). The customer's name and address and the reference are personal data,
 * so it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param order the order
 * @param customer its customer
 * @param customerName the customer's name, or empty once the customer is anonymized (BR-98)
 * @param address the customer's address as typed, or empty once the customer is anonymized
 * @param window its time window, on its date (BR-49)
 * @param weight its weight demand (BR-50)
 * @param volume its volume demand (BR-50)
 * @param priority its priority (BR-53)
 * @param leaveAtDoor whether it may be left at the door (FR-364)
 * @param reference its outside reference, if any (FR-85)
 * @param attempt its current attempt (FR-258)
 * @param cancelled whether it is cancelled (FR-58)
 */
public record OrderRow(
        OrderId order,
        CustomerId customer,
        Optional<ContactName> customerName,
        Optional<Address> address,
        TimeWindow window,
        Weight weight,
        Volume volume,
        Priority priority,
        boolean leaveAtDoor,
        Optional<ExternalReference> reference,
        Attempt attempt,
        boolean cancelled)
        implements PersonalData {

    /** Checks every part is present, possibly empty. */
    public OrderRow {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(attempt, "attempt");
    }

    /**
     * A form that hides the customer's name and address and the reference.
     *
     * @return {@code OrderRow[redacted]}
     */
    @Override
    public String toString() {
        return "OrderRow[redacted]";
    }
}
