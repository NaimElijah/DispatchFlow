package com.dispatchflow.planning.application.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * One stop's planned times and the leg into it, as the optimizer timed them and the plan stores
 * them (FR-17, FR-23, ADR-052, ADR-067). Live Operations builds its {@code PlannedStopLeg} from the
 * leg and the service, {@code departure} less {@code serviceStart}, and propagates delay along them
 * without re-applying a factor (DL §10.4).
 *
 * <p>Built in M5.
 *
 * @param travelIn the buffered travel from the depot or the stop before it, never negative
 * @param arrival when the vehicle is planned to reach the stop
 * @param serviceStart when service is planned to start; later than the arrival only after a wait
 *     (BR-03)
 * @param departure when the vehicle is planned to leave (BR-04)
 */
public record PlannedStopTimes(Duration travelIn, Instant arrival, Instant serviceStart, Instant departure) {

    /** Checks every part is present. */
    public PlannedStopTimes {
        Objects.requireNonNull(travelIn, "travelIn");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(serviceStart, "serviceStart");
        Objects.requireNonNull(departure, "departure");
    }
}
