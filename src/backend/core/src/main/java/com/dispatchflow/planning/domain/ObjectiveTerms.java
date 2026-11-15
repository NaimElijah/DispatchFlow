package com.dispatchflow.planning.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * A re-optimization's objective, term by term, for the preview's change in totals (BR-09, BR-56,
 * FR-103). The objective counts seconds, never money (DL §3.3). It mirrors the Python {@code
 * objective.Cost} one term to one, in the same order (ADR-113).
 *
 * <p>{@code orphanedStops} and {@code movedStops} together are the route-stability cost: the
 * moved-stop cost times the stops that leave their vehicle, dropped stops included, with no stop
 * counted in both (FR-256, BR-115). The preview's stability figure is their sum, {@link
 * #routeStability()} (FR-103).
 *
 * @param travel the unbuffered travel time of every leg (FR-402)
 * @param drops the drop penalties, without the moved-stop cost (BR-54, BR-126)
 * @param doorWindow the leave-at-door window penalty (BR-120)
 * @param fixedVehicles the fixed vehicle cost of the vehicles used, when on (FR-21)
 * @param lateness the lateness penalty (BR-107, BR-115)
 * @param orphanedStops the moved-stop cost of the stops on a vehicle that is out (BR-106, BR-115)
 * @param movedStops the moved-stop cost of the stops that stay on their vehicle and are dropped
 *     (BR-115, FR-256)
 */
public record ObjectiveTerms(
        Duration travel,
        Duration drops,
        Duration doorWindow,
        Duration fixedVehicles,
        Duration lateness,
        Duration orphanedStops,
        Duration movedStops) {

    /** Checks every term is present. */
    public ObjectiveTerms {
        Objects.requireNonNull(travel, "travel");
        Objects.requireNonNull(drops, "drops");
        Objects.requireNonNull(doorWindow, "doorWindow");
        Objects.requireNonNull(fixedVehicles, "fixedVehicles");
        Objects.requireNonNull(lateness, "lateness");
        Objects.requireNonNull(orphanedStops, "orphanedStops");
        Objects.requireNonNull(movedStops, "movedStops");
    }

    /**
     * The route-stability cost the preview shows (FR-103, FR-256, BR-115).
     *
     * @return the orphaned stops' cost plus the moved stops'
     */
    public Duration routeStability() {
        return orphanedStops.plus(movedStops);
    }
}
