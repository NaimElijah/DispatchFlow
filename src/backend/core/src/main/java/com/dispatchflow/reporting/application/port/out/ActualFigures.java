package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.Distance;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * What a plan's vehicles actually drove, beside its planned figures (FR-106). How each is measured
 * waits on the pilot protocol (Q-40), so each may be unknown.
 *
 * <p>Built in M5.
 *
 * @param distance the distance driven over every route, or empty when it was not measured
 * @param drivingTime the time driven over every route, or empty when it was not measured
 */
public record ActualFigures(Optional<Distance> distance, Optional<Duration> drivingTime) {

    /** Checks both parts are present, possibly empty. */
    public ActualFigures {
        Objects.requireNonNull(distance, "distance");
        Objects.requireNonNull(drivingTime, "drivingTime");
    }
}
