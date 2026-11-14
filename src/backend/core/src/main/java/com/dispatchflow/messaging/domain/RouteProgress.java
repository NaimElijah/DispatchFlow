package com.dispatchflow.messaging.domain;

/**
 * How far a stop's route has come, as Live Operations reports it when a trigger runs (FR-352 to
 * FR-354). Messaging keeps its own copy of the concept (DL §2.3, rule 1).
 */
public enum RouteProgress {
    /** The driver has not started the route. */
    NOT_STARTED,
    /** Started and not finished. */
    IN_PROGRESS,
    /** Finished. */
    FINISHED;

    /**
     * Whether the route has started, which "time changed" and "your delivery changed" need (FR-353,
     * FR-354).
     *
     * @return {@code true} once started, finished included
     */
    public boolean hasStarted() {
        return this != NOT_STARTED;
    }
}
