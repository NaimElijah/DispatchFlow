package com.dispatchflow.planning.application.port.out.solvejob;

import com.dispatchflow.planning.domain.plan.SolverName;
import java.util.List;

/**
 * The optimization service, asked to solve a day or re-optimize a published plan (DL §2.1, TS §6.4,
 * TS §6.5, ADR-002). The optimizer adapter implements it over {@code optimizer-api} v1 as an
 * anti-corruption layer, with plain synchronous HTTP and a client timeout of the limit plus 30
 * seconds (BR-32, BR-84, ADR-051). A job's worker calls it outside any transaction (DL §6.3).
 *
 * <p>Built in M0, M4 and M9, method by method.
 */
public interface RouteOptimizer {

    /**
     * Solves a day (FR-15, FR-17, FR-18).
     *
     * <p>Built in M0.
     *
     * @param request the daily solve
     * @return the validated solution, or the failure: the optimizer unreachable, the call past its
     *     timeout, or the validator's rejection (FR-65)
     */
    DailyAnswer solve(SolveRequest request);

    /**
     * Re-optimizes a published plan's remaining stops (FR-35, ADR-050).
     *
     * <p>Built in M9.
     *
     * @param request the re-optimization
     * @return the validated solution with the preview's facts, or the failure
     */
    ReoptimizationAnswer reoptimize(ReoptimizationRequest request);

    /**
     * The solvers the optimizer offers, by name, so the dispatcher chooses one (FR-20, FR-224). The
     * Python {@code ListSolvers} answers it.
     *
     * <p>Built in M4.
     *
     * @return the names, in the optimizer's order
     * @throws java.io.UncheckedIOException when the optimizer can't be reached
     */
    List<SolverName> solvers();
}
