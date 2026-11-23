package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.delivery.DeliveryStatus;
import com.dispatchflow.liveops.domain.delivery.HeldReport;
import com.dispatchflow.liveops.domain.delivery.OutcomeRecord;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A stop's history: its arrival, the original outcome with every correction beside it, and its held
 * reports, which a dispatcher reads before correcting or deciding (FR-333, FR-404, BR-132). Its
 * records hold reasons, proofs and notes, so it prints redacted.
 *
 * <p>Built in M8.
 *
 * @param stop the stop
 * @param order its order
 * @param status its delivery's status
 * @param arrivedAt when the driver arrived, corrected for the device's clock, or empty when no
 *     arrival is recorded
 * @param outcomes the outcomes on record, the original first and the effective one last; empty
 *     when it has none
 * @param held every report held on it, in the order they were held, with its state
 * @param version the delivery's version, which a correction or a decision on a held report sends
 *     back (BR-76)
 */
public record StopHistoryView(
        StopId stop,
        OrderId order,
        DeliveryStatus status,
        Optional<Instant> arrivedAt,
        List<OutcomeRecord> outcomes,
        List<HeldReport> held,
        Version version)
        implements PersonalData {

    /** Checks every part is present, possibly empty, and copies the lists. */
    public StopHistoryView {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(arrivedAt, "arrivedAt");
        outcomes = List.copyOf(outcomes);
        held = List.copyOf(held);
        Objects.requireNonNull(version, "version");
    }

    /**
     * A form that names the stop and leaves its records out.
     *
     * @return the stop and its status, redacted
     */
    @Override
    public String toString() {
        return "StopHistoryView[stop=" + stop + ", status=" + status + ", redacted]";
    }
}
