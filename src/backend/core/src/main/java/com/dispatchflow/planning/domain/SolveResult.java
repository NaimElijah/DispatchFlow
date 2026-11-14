package com.dispatchflow.planning.domain;

import java.util.List;

/**
 * A daily solve's validated result, as its job keeps it (DL §9.7, ADR-101). Only {@link
 * SolveJob#succeed(ValidatedSolution, java.time.Instant)} takes one from a {@link ValidatedSolution},
 * and only persistence rebuilds one through {@code SolveJob.restore}, so a plan applying it applies
 * what the validator accepted (BR-15).
 *
 * @param routes the routes, with the validator's times
 * @param dropped the drops, each with its reason
 */
public record SolveResult(List<SolvedRoute> routes, List<DroppedOrder> dropped) {

    /** Copies the lists. */
    public SolveResult {
        routes = List.copyOf(routes);
        dropped = List.copyOf(dropped);
    }
}
