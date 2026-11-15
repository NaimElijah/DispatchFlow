package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.reporting.domain.Distance;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * What a plan's vehicles actually drove, beside its planned figures (FR-106). Q-40 settled that
 * actuals come from the apps' report times and the position traces (NFR-249), but no milestone
 * measures the distance and driving time from the traces yet, so each may be unknown.
 *
 * <p>Built in an unscheduled milestone (Q-40); the actual arrivals beside them, in {@link
 * PlannedVsActualView}, in M5.
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
