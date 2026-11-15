package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import java.time.Duration;
import java.util.Objects;

/**
 * A stop a re-optimization keeps past its window end, flagged in the preview without refusing
 * acceptance (BR-107, FR-103).
 *
 * @param order the order
 * @param attempt its attempt
 * @param late how far past the window end its service starts
 */
public record LateStop(OrderId order, Attempt attempt, Duration late) {

    /** Checks every part is present. */
    public LateStop {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(late, "late");
    }
}
