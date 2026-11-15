package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import java.util.Objects;

/**
 * One stop of a solved route, before the plan gives it a {@code StopId} (FR-17).
 *
 * @param order the order served
 * @param attempt the attempt served
 * @param plan its times and the leg into it
 */
public record SolvedStop(OrderId order, Attempt attempt, StopPlan plan) {

    /** Checks every part is present. */
    public SolvedStop {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(plan, "plan");
    }
}
