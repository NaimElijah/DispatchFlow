package com.dispatchflow.liveops.application.api;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What happened at one stop of a plan, as Reporting's on-time rate judges it (FR-264, FR-340,
 * FR-370, ADR-109). Reporting builds its {@code StopResult} from it and the order's window and door
 * flag, which Customers &amp; Orders' {@code OrdersForDate} gives.
 *
 * <p>Built in M5.
 *
 * @param stop the stop
 * @param order its order
 * @param arrivedAt when the driver arrived, as corrected for the device's clock (BR-114); empty
 *     when no arrival was reported (FR-340)
 * @param outcome the stop's effective outcome
 */
public record StopResultFact(StopId stop, OrderId order, Optional<Instant> arrivedAt, StopOutcomeFact outcome) {

    /** Checks every part is present, possibly empty. */
    public StopResultFact {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(arrivedAt, "arrivedAt");
        Objects.requireNonNull(outcome, "outcome");
    }
}
