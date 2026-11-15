package com.dispatchflow.messaging.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * When a stop is now expected to be reached (FR-254, DL §10.4), as Live Operations'
 * {@code ArrivalProjections} answers it; the application layer translates that answer into this
 * value (DL §2.3, rule 1).
 *
 * @param at the projected arrival, never before the time it was projected at (FR-254)
 * @param basis whether the arrival was carried forward from what happened on the route, or is a
 *     skipped stop's planned arrival (ADR-114)
 */
public record ProjectedArrival(Instant at, Basis basis) {

    /** What a projected arrival rests on (ADR-114). */
    public enum Basis {
        /** Carried forward along the stored legs from the route's start or its latest event. */
        CARRIED_FORWARD,
        /**
         * A stop skipped before the route's latest event: its planned arrival, never earlier than
         * now. Nothing observed moves it, so it only follows the clock.
         */
        PLANNED
    }

    /** Checks both parts are present. */
    public ProjectedArrival {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(basis, "basis");
    }

    /**
     * Whether the arrival was carried forward from what happened on the route, so a change in it
     * means something to the recipient (FR-352, FR-353, ADR-114).
     *
     * @return {@code true} for {@link Basis#CARRIED_FORWARD}
     */
    public boolean isCarriedForward() {
        return basis == Basis.CARRIED_FORWARD;
    }
}
