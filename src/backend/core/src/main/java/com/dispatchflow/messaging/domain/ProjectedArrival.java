package com.dispatchflow.messaging.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * When a stop is now expected to be reached (FR-254, DL §10.4), as Live Operations'
 * {@code ArrivalProjections} answers it; the application layer translates that answer into this
 * value (DL §2.3, rule 1).
 *
 * @param at the projected arrival, never before the time it was projected at (FR-254)
 */
public record ProjectedArrival(Instant at) {

    /** Checks the time is present. */
    public ProjectedArrival {
        Objects.requireNonNull(at, "at");
    }
}
