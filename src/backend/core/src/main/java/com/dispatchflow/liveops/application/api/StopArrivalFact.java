package com.dispatchflow.liveops.application.api;

import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A stop's projected arrival and how far it and its route have come, as Live Operations answers
 * Messaging's minute job (FR-351 to FR-353, FR-373, DL §10.3, ADR-067, ADR-114). Messaging builds
 * its {@code ProjectedArrival} and {@code ArrivalFacts} from it.
 *
 * <p>Built in M8.
 *
 * @param stop the stop
 * @param arrival the stop's projected arrival, or empty when it has none: a stop with an arrival
 *     or an outcome, or one no route in force holds
 * @param routeProgress how far the stop's route has come
 * @param previousStop the stop before it in plan order
 * @param stopProgress how far the stop itself has come
 */
public record StopArrivalFact(
        StopId stop,
        Optional<Projection> arrival,
        RouteProgress routeProgress,
        PreviousStop previousStop,
        StopProgress stopProgress) {

    /** Checks every part is present, possibly empty. */
    public StopArrivalFact {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(routeProgress, "routeProgress");
        Objects.requireNonNull(previousStop, "previousStop");
        Objects.requireNonNull(stopProgress, "stopProgress");
    }

    /**
     * What a projected arrival rests on (ADR-114).
     *
     * <p>Built in M8.
     */
    public enum Basis {
        /** Carried forward along the stored legs from the route's start or its latest event. */
        CARRIED_FORWARD,
        /** A stop skipped before the route's latest event: its planned arrival, never earlier than now. */
        PLANNED
    }

    /**
     * How far a stop's route has come (FR-352 to FR-354).
     *
     * <p>Built in M8.
     */
    public enum RouteProgress {
        /** The driver has not started the route. */
        NOT_STARTED,
        /** Started and not finished. */
        IN_PROGRESS,
        /** Finished. */
        FINISHED
    }

    /**
     * The stop before this one in plan order (FR-352).
     *
     * <p>Built in M8.
     */
    public enum PreviousStop {
        /** This stop is the route's first. */
        NONE,
        /** The stop before has an outcome. */
        HAS_OUTCOME,
        /** The stop before has no outcome yet. */
        NO_OUTCOME
    }

    /**
     * How far a stop has come (FR-373).
     *
     * <p>Built in M8.
     */
    public enum StopProgress {
        /** Neither arrived at nor given an outcome. */
        NOT_REACHED,
        /** Arrived at, with no outcome yet. */
        ARRIVED,
        /** Delivered or failed. */
        HAS_OUTCOME
    }

    /**
     * When the stop is now expected to be reached, and on what basis (FR-254, ADR-067, ADR-114).
     *
     * <p>Built in M8.
     *
     * @param at the projected arrival, never before the time it was projected at (FR-254)
     * @param basis whether it was carried forward or is a skipped stop's planned arrival
     */
    public record Projection(Instant at, Basis basis) {

        /** Checks both parts are present. */
        public Projection {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(basis, "basis");
        }
    }
}
