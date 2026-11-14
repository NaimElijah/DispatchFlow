package com.dispatchflow.planning.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * A re-optimization's objective, term by term, for the preview's change in totals (BR-09, BR-56,
 * FR-103). The objective counts seconds, never money (DL §3.3).
 *
 * @param travel the travel time
 * @param drops the drop penalties
 * @param window the leave-at-door window penalty (BR-120)
 * @param stability the route-stability penalty (FR-37, BR-115)
 * @param lateness the lateness penalty (BR-107, BR-115)
 * @param fixedVehicleCost the fixed vehicle cost, when on (FR-21)
 */
public record ObjectiveTerms(
        Duration travel,
        Duration drops,
        Duration window,
        Duration stability,
        Duration lateness,
        Duration fixedVehicleCost) {

    /** Checks every term is present. */
    public ObjectiveTerms {
        Objects.requireNonNull(travel, "travel");
        Objects.requireNonNull(drops, "drops");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(stability, "stability");
        Objects.requireNonNull(lateness, "lateness");
        Objects.requireNonNull(fixedVehicleCost, "fixedVehicleCost");
    }
}
