package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.AccessDetails;
import com.dispatchflow.orders.domain.DriverNote;
import com.dispatchflow.orders.domain.Recipient;
import com.dispatchflow.orders.domain.ServiceTime;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything one order holds, as the dispatcher sees and changes it (FR-09, FR-57, FR-85 to
 * FR-87, FR-135, FR-364). The version is the one a change sends back (BR-76). It holds personal
 * data, so it prints redacted.
 *
 * <p>Built in M2.
 *
 * @param row what the order's list line shows
 * @param serviceTime its service time, its own or its customer's default (BR-124)
 * @param recipient its own recipient, or {@code Recipient.NONE} (FR-87)
 * @param note its own delivery note, if any (FR-135)
 * @param access its floor, apartment and entrance code (FR-86)
 * @param anonymized whether its personal fields were removed (NFR-257)
 * @param lastChangedAt when it was last placed, changed, moved, cancelled or put back (FR-397)
 * @param version the version the order was read at, for {@code If-Match} (BR-76)
 */
public record OrderDetail(
        OrderRow row,
        ServiceTime serviceTime,
        Recipient recipient,
        Optional<DriverNote> note,
        AccessDetails access,
        boolean anonymized,
        Instant lastChangedAt,
        Version version)
        implements PersonalData {

    /** Checks every part is present, possibly empty. */
    public OrderDetail {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(serviceTime, "serviceTime");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(lastChangedAt, "lastChangedAt");
        Objects.requireNonNull(version, "version");
    }

    /**
     * A form that hides the order's personal data.
     *
     * @return {@code OrderDetail[redacted]}
     */
    @Override
    public String toString() {
        return "OrderDetail[redacted]";
    }
}
