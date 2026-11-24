package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.solvejob.RouteOptimizer;
import com.dispatchflow.planning.application.service.SolverNotOffered;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.NotFound;
import java.util.Optional;

/**
 * Requests a re-optimization of a published plan's remaining stops, after a disruption (FR-35,
 * FR-332, BR-38, BR-84, DL §6.2). Only a person starts one (FR-332). {@code
 * Plan.requestReoptimization} checks the plan and isn't saved, so the request takes no plan
 * version; the new job, with a 20-second limit, joins the re-optimization lane, read under its lock
 * (ADR-101). With no solver chosen it runs the planning settings' default solver for a
 * re-optimization (FR-20, ADR-004).
 *
 * <p>Before its transaction it checks the chosen solver against {@link RouteOptimizer#solvers()},
 * which lists only FR-20's solvers, and refuses one the optimizer doesn't offer with {@link
 * SolverNotOffered}, so no job is queued for a solver the optimizer would refuse (FR-224, DL §6.3).
 * Planning's domain has no error for it, because only the optimizer knows its solvers. When the
 * optimizer can't be reached for the list, {@code solvers()} throws {@code OptimizerUnavailable},
 * and the request goes on unchecked: the job is queued, and fails as <em>optimizer unavailable</em>
 * (FR-65), as it did before the check.
 *
 * <p>{@code PlanningSettings} has no default solver of each kind yet, which ADR-004 puts there: a
 * domain gap that M4 closes. Until M4 adds the field, a request with no solver runs OR-Tools, the
 * default that ADR-004 and FR-20 name.
 *
 * <p>It sends no chosen routes: FR-159, re-optimizing only chosen routes, is left out, because
 * ADR-101 point 32 deferred it while no rule of the domain needs it. Every route of the plan's
 * current revision goes to the optimizer.
 *
 * <p>{@link RequestReoptimizationChange} holds the transaction, which queues the job and writes the
 * audit record; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M9.
 */
public final class RequestReoptimization {

    private final RouteOptimizer optimizer;
    private final LiveUpdates liveUpdates;
    private final RequestReoptimizationChange change;

    /**
     * Creates the use case.
     *
     * @param optimizer the optimization service, for the solvers it offers
     * @param liveUpdates the dispatchers' open screens
     * @param change the request's transaction
     */
    public RequestReoptimization(
            RouteOptimizer optimizer, LiveUpdates liveUpdates, RequestReoptimizationChange change) {
        this.optimizer = optimizer;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Requests the re-optimization.
     *
     * <p>Built in M9.
     *
     * @param actor the dispatcher
     * @param plan the published plan
     * @param solver the solver, or empty for the settings' re-optimization default (FR-20, ADR-004)
     * @return the new job's ID
     * @throws NotFound when no plan has the ID
     * @throws SolverNotOffered when the optimizer doesn't offer the chosen solver
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished},
     *     {@code DateInPast} (BR-118), {@code ReoptimizationAlreadyRunning} (BR-38), or {@code
     *     SolveQueueFull} when the lane holds 5 queued jobs (BR-112)
     */
    public JobId request(Actor actor, PlanId plan, Optional<SolverName> solver) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
