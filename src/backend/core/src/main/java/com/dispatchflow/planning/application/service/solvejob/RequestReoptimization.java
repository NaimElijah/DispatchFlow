package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.OptimizerUnavailable;
import com.dispatchflow.planning.application.port.out.Seeds;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.settings.PlanningSettingsRepository;
import com.dispatchflow.planning.application.port.out.solvejob.RouteOptimizer;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolverNotOffered;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import java.util.Optional;

/**
 * Requests a re-optimization of a published plan's remaining stops, after a disruption (FR-35, FR-332,
 * BR-38, BR-84, DL §6.2). Only a person starts one (FR-332). {@code Plan.requestReoptimization} checks
 * the plan and isn't saved, so the request takes no plan version; the new job, with a 20-second limit,
 * joins the re-optimization lane, read under its lock (ADR-101). With no solver chosen it runs the
 * planning settings' default solver for a re-optimization (FR-20, ADR-004).
 *
 * <p>Before its transaction it checks the chosen solver against {@link RouteOptimizer#solvers()},
 * which lists only FR-20's solvers, and refuses one the optimizer doesn't offer with {@link
 * SolverNotOffered}, so no job is queued for a solver the optimizer would refuse (FR-224, DL §6.3).
 * Planning's domain has no error for it, because only the optimizer knows its solvers.
 *
 * <p>{@code PlanningSettings} has no default solver of each kind yet, which ADR-004 puts there: a
 * domain gap that M4 closes. Until M4 adds the field, a request with no solver runs OR-Tools, the
 * default that ADR-004 and FR-20 name.
 *
 * <p>Built in M9.
 */
public final class RequestReoptimization {

    private final PlanRepository plans;
    private final SolveJobRepository jobs;
    private final PlanningSettingsRepository settings;
    private final RouteOptimizer optimizer;
    private final Seeds seeds;
    private final Clock clock;
    private final IdGenerator ids;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept
     * @param jobs where solve jobs are kept, and the re-optimization lane's capacity under its lock
     * @param settings the planning settings, for the default solver
     * @param optimizer the optimization service, for the solvers it offers
     * @param seeds the new job's seed (NFR-13)
     * @param clock the time, for {@code today} and {@code now}
     * @param ids the new job's ID
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the job's transaction (TS §6.2)
     */
    public RequestReoptimization(
            PlanRepository plans,
            SolveJobRepository jobs,
            PlanningSettingsRepository settings,
            RouteOptimizer optimizer,
            Seeds seeds,
            Clock clock,
            IdGenerator ids,
            LiveUpdates liveUpdates,
            AuditLog auditLog) {
        this.plans = plans;
        this.jobs = jobs;
        this.settings = settings;
        this.optimizer = optimizer;
        this.seeds = seeds;
        this.clock = clock;
        this.ids = ids;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
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
     * @throws OptimizerUnavailable when the optimizer can't be reached for its solvers
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished}, {@code
     *     DateInPast} (BR-118), {@code ReoptimizationAlreadyRunning} (BR-38), or {@code SolveQueueFull}
     *     when the lane holds 5 queued jobs (BR-112)
     */
    public JobId request(Actor actor, PlanId plan, Optional<SolverName> solver) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
