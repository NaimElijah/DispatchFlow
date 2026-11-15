package com.dispatchflow.planning.domain.solution;

import java.util.List;

/**
 * A daily solve's validated result, as its job keeps it (DL §9.7, ADR-101). Only {@link
 * com.dispatchflow.planning.domain.plan.SolveJob#succeed(ValidatedSolution, java.time.Instant)}
 * takes one from a {@link ValidatedSolution}, and only persistence rebuilds one through {@code
 * SolveJob.restore}, so a plan applying it applies what the validator accepted (BR-15).
 *
 * @param routes the routes, with the validator's times
 * @param dropped the drops, each with its reason
 * @param excluded the vehicles the solve left out, each with its cause (FR-197, ADR-112)
 */
public record SolveResult(List<SolvedRoute> routes, List<DroppedOrder> dropped, List<ExcludedVehicle> excluded) {

    /** Copies the lists. */
    public SolveResult {
        routes = List.copyOf(routes);
        dropped = List.copyOf(dropped);
        excluded = List.copyOf(excluded);
    }
}
