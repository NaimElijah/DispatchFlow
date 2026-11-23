package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A delivery's stored state: what {@link Delivery#snapshot()} returns and {@link Delivery#restore}
 * rebuilds from. Its records hold reasons, proofs and notes, so it is personal data, and {@link
 * #toString()} leaves them out.
 *
 * @param stop the stop, which keys the delivery
 * @param order the stop's order
 * @param attempt the stop's attempt
 * @param version the version it was loaded at, or null for an aggregate never saved
 *     (ADR-126)
 * @param arrival the driver's arrival, if any (FR-29)
 * @param outcomes the original outcome first, then each correction in order (FR-333)
 * @param heldReports every report held for a dispatcher, in the order held (BR-132)
 */
public record DeliverySnapshot(
        StopId stop,
        OrderId order,
        Attempt attempt,
        @Nullable Version version,
        Optional<ArrivalRecord> arrival,
        List<OutcomeRecord> outcomes,
        List<HeldReport> heldReports)
        implements PersonalData {

    /** Checks every field is present, and copies the lists. */
    public DeliverySnapshot {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(arrival, "arrival");
        outcomes = List.copyOf(outcomes);
        heldReports = List.copyOf(heldReports);
    }

    /**
     * A form that names the stop and counts its records, and leaves their content out.
     *
     * @return the snapshot, redacted
     */
    @Override
    public String toString() {
        return "DeliverySnapshot[stop=" + stop + ", order=" + order + ", attempt=" + attempt + ", version="
                + version + ", arrival=" + arrival + ", outcomes=" + outcomes.size() + ", heldReports="
                + heldReports.size() + "]";
    }
}
