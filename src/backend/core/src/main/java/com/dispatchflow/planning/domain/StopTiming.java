package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.StopId;
import java.util.Objects;

/**
 * The validator's times for one stop of a plan, inside an {@link Evaluation}.
 *
 * @param stop the stop
 * @param plan its times and the leg into it
 */
public record StopTiming(StopId stop, StopPlan plan) {

    /** Checks both parts are present. */
    public StopTiming {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(plan, "plan");
    }
}
