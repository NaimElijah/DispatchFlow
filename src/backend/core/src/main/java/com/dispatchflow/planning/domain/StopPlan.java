package com.dispatchflow.planning.domain;

import java.util.Objects;

/**
 * A stop's planned times and the leg into it, as the validator timed them (FR-17, FR-23, BR-68).
 *
 * @param times when the vehicle arrives, starts serving and leaves
 * @param legIn the leg from the previous stop or the start
 */
public record StopPlan(StopTimes times, TravelLeg legIn) {

    /** Checks both parts are present. */
    public StopPlan {
        Objects.requireNonNull(times, "times");
        Objects.requireNonNull(legIn, "legIn");
    }
}
