package com.dispatchflow.planning.application.port.out.solvejob;

import com.dispatchflow.planning.application.port.out.OptimizerRefusedRequest;
import com.dispatchflow.planning.application.port.out.OptimizerUnavailable;
import com.dispatchflow.planning.domain.plan.SolverName;
import java.util.List;

/**
 * The optimization service, asked to solve a day or re-optimize a published plan (DL §2.1, TS §6.4,
 * TS §6.5, ADR-002). The optimizer adapter implements it over {@code optimizer-api} v1 as an
 * anti-corruption layer, with plain synchronous HTTP and a client timeout of the limit plus 30
 * seconds (BR-32, BR-84, ADR-051). A job's worker calls it outside any transaction (DL §6.3).
 *
 * <p>What the optimizer answers becomes, on the core's side:
 *
 * <ul>
 *   <li>a validated solution: the answer's {@code Solved};
 *   <li>the validator's rejection: {@code Failed} with {@code SolveFailure.rejected} and every
 *       violation (BR-57);
 *   <li>its own failure, its solver failed or found no solution within its budget, which the
 *       optimizer sends as a closed kind, {@code SOLVER_FAILED} or {@code NO_SOLUTION_WITHIN_BUDGET}
 *       (ADR-004): {@code Failed}, with the reason the gap below waits for;
 *   <li>no answer, the optimizer unreachable or past the client timeout: {@code Failed} as
 *       <em>optimizer unavailable</em> or <em>overran</em> (FR-65, BR-32);
 *   <li>a refused request, a solver the optimizer doesn't hold, a budget it can't honour or a problem
 *       it finds invalid: {@link OptimizerRefusedRequest}. It is a defect of the core's request, or a
 *       race with a redeploy that dropped a solver, since {@code RequestSolve} and {@code
 *       RequestReoptimization} check the name against {@link #solvers()} first; the worker fails the
 *       job with it, so the plan leaves <em>solving</em>.
 * </ul>
 *
 * <p><b>The optimizer's own failure.</b> The adapter maps the optimizer's kind to the {@code
 * SolveFailureReason} of the same name, {@code SOLVER_FAILED} or {@code NO_SOLUTION_WITHIN_BUDGET},
 * and a refused request to {@code REQUEST_REFUSED} (FR-65, ADR-121). It never maps either to {@code
 * VALIDATOR_REJECTED}, which means the validator rejected a solution with every violation (BR-57).
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
     * @return the validated solution with its objective and stats, or the failure: the optimizer
     *     unreachable, the call past its timeout, the validator's rejection (FR-65), or the
     *     optimizer's own failure, whose reason M0 adds (see the gap above)
     * @throws OptimizerRefusedRequest when the optimizer refuses the request
     */
    DailyAnswer solve(SolveRequest request);

    /**
     * Re-optimizes a published plan's remaining stops (FR-35, ADR-050).
     *
     * <p>Built in M9.
     *
     * @param request the re-optimization
     * @return the validated solution with what the optimizer computed for the preview, or the
     *     failure, as for {@link #solve}
     * @throws OptimizerRefusedRequest when the optimizer refuses the request
     */
    ReoptimizationAnswer reoptimize(ReoptimizationRequest request);

    /**
     * The solvers the optimizer offers, by name, so the dispatcher chooses one (FR-20, FR-224). The
     * Python {@code ListSolvers} answers it, from a registry that holds only FR-20's solvers, so the
     * baseline's is never offered.
     *
     * <p>Built in M4.
     *
     * @return the names, in the optimizer's order
     * @throws OptimizerUnavailable when the optimizer can't be reached
     */
    List<SolverName> solvers();
}
