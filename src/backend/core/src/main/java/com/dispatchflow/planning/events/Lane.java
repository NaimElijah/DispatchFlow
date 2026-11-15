package com.dispatchflow.planning.events;

/** One of the two job queues a solve waits in (DL §17, BR-112, ADR-051). */
public enum Lane {
    /** Daily solves, which compute a date's routes from scratch. */
    DAILY,
    /** Re-optimizations of a published plan (FR-35). */
    REOPTIMIZATION
}
