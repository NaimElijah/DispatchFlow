package com.dispatchflow.reporting.domain;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.TimeWindow;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What happened at one stop of a plan, as Reporting reads it from the views of Planning, Customers
 * &amp; Orders and Live Operations (DL §2.3, rules 1 and 3). {@link OnTimeRate} judges it.
 *
 * @param stop the stop
 * @param order its order, which may be cancelled (BR-30)
 * @param window the order's window
 * @param leaveAtDoor whether the order may be left at the door, so its window is a preference
 *     (BR-119, FR-370)
 * @param arrivedAt when the driver's device recorded the arrival, as corrected for its clock
 *     (BR-114); empty when no arrival was reported (FR-340)
 * @param outcome the effective outcome, after any correction (FR-333)
 */
public record StopResult(
        StopId stop,
        OrderId order,
        TimeWindow window,
        boolean leaveAtDoor,
        Optional<Instant> arrivedAt,
        StopOutcome outcome) {

    /** Checks every part is present. */
    public StopResult {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(arrivedAt, "arrivedAt");
        Objects.requireNonNull(outcome, "outcome");
    }
}
