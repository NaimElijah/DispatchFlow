package com.dispatchflow.planning.domain.plan;

/**
 * How a dispatcher fixed a route against edits and solves (FR-147). FR-147 names the two kinds but
 * not what the second keeps; ADR-101 reads it.
 */
public enum RouteLock {
    /** The route keeps its vehicle, its driver and its exact stop sequence. */
    WHOLE_ROUTE,
    /** The route's stops keep their order, on whichever one route serves them. */
    STOP_SEQUENCE
}
