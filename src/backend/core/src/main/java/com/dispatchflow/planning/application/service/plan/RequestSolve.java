package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.audit.application.api.Refusals;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.service.SolverNotOffered;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import java.util.Optional;

/**
 * Requests a daily solve of a draft or ready plan: the plan becomes solving, and a job joins the
 * daily lane in the same transaction, read under the lane's lock (FR-15, FR-16, FR-19, FR-20,
 * FR-92, FR-224, FR-225, BR-112, DL §6.1, §6.2). Solving again discards the plan's manual edits,
 * which the screen warns about first (FR-92). With no limit it runs the planning settings' default
 * limit (FR-19, FR-183).
 *
 * <p><b>The solver.</b> Until the solver choice is built, it offers OR-Tools alone and asks the
 * optimizer nothing: a request with no solver runs OR-Tools, the default ADR-004 and FR-20 name, and
 * a request that names any other solver is refused with {@link SolverNotOffered} before the
 * transaction, so no job is queued for a solver the optimizer may not hold (FR-224, DL §6.3).
 * Planning's domain has no error for it, because only the optimizer knows its solvers. The solver
 * choice brings the check against {@code RouteOptimizer#solvers()}, which lists only FR-20's
 * solvers, with its rule for an optimizer that can't be reached (ADR-121 P16), and {@code
 * PlanningSettings}' default solver of each kind, which ADR-004 puts there.
 *
 * <p>{@link RequestSolveChange} holds the transaction, which makes the plan solving, queues the job
 * and writes the audit record of its success; after it commits, the dispatchers' open screens are
 * told of the job and of the plan (ADR-129).
 *
 * <p>A refused request, the solver's refusal included, is recorded once the change has rolled back,
 * in a transaction of its own, through {@link Refusals}, naming the plan (NFR-23, ADR-110, ADR-149,
 * ADR-150). A {@code StaleVersion} is a race, which the call wrapper records (ADR-140 decision 6).
 */
public final class RequestSolve {

    private final LiveUpdates liveUpdates;
    private final RequestSolveChange change;
    private final AuditLog auditLog;
    private final Clock clock;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the request's transaction
     * @param auditLog the audit trail, which a refused request is recorded in
     * @param clock the time a refused request is recorded at
     */
    public RequestSolve(LiveUpdates liveUpdates, RequestSolveChange change, AuditLog auditLog, Clock clock) {
        this.liveUpdates = liveUpdates;
        this.change = change;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * Requests the solve.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param solver the solver, or empty for OR-Tools (FR-20, ADR-004)
     * @param limit the search limit, or empty for the settings' default (FR-19)
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @return the new job's ID
     * @throws NotFound when no plan has the ID, once its refused record is stored
     * @throws SolverNotOffered when a solver other than OR-Tools is named, once its refused record is
     *     stored
     * @throws StaleVersion when the plan changed since {@code expected}, unrecorded here
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotDraftOrReady},
     *     {@code DateInPast} (BR-118), or {@code SolveQueueFull} when the daily lane holds 5 queued
     *     jobs (BR-112), once its refused record is stored
     */
    public JobId request(
            Actor actor, PlanId plan, Optional<SolverName> solver, Optional<SolveLimit> limit, Version expected) {
        JobId job = Refusals.recordedBy(auditLog, actor.call(), clock.now(), List.of(AuditedRecord.of(plan)), () -> {
            solver.filter(name -> !name.equals(SolverName.OR_TOOLS)).ifPresent(name -> {
                throw new SolverNotOffered(name);
            });
            return change.apply(actor, plan, solver, limit, expected);
        });
        liveUpdates.jobChanged(job, plan);
        liveUpdates.planChanged(plan);
        return job;
    }
}
