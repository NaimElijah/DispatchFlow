package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.shared.StopId;
import java.util.Objects;
import java.util.Optional;

/**
 * The validator's times for one stop of a plan, inside an {@link Evaluation}. A stop it couldn't
 * time, because it breaks BR-14 or, in a re-optimization, BR-10, has none, and keeps none in the
 * plan (ADR-112, ADR-113).
 *
 * @param stop the stop
 * @param plan its times and the leg into it, or empty when the validator couldn't time it
 */
public record StopTiming(StopId stop, Optional<StopPlan> plan) {

    /** Checks both parts are present, the plan possibly empty. */
    public StopTiming {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(plan, "plan");
    }
}
