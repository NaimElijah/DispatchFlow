package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.events.SolveSucceeded;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Applies a succeeded daily solve to its plan: the job's validated routes, drops and excluded vehicles
 * replace the plan's, and the plan becomes ready (BR-15, BR-59, FR-18, FR-197, DL §6.2). Its
 * listener calls it on {@link SolveSucceeded}. A re-optimization's event changes no plan: its
 * proposal waits for a dispatcher (FR-255).
 *
 * <p>It is idempotent by nature, so a repeated delivery is safe: {@code Plan.applySolveResult}
 * ignores a job the plan no longer waits for, an abandoned plan included (ADR-101). It writes its
 * audit record with the listener's server step (ADR-110).
 *
 * <p>Built in M0.
 */
public final class ApplySolveResult {

    private final PlanRepository plans;
    private final SolveJobRepository jobs;
    private final IdGenerator ids;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept
     * @param jobs where solve jobs are kept, for the job's result
     * @param ids new IDs for the result's new routes and stops: one per route and one per stop
     *     (ADR-112)
     * @param clock the time, for {@code now}
     * @param liveUpdates the dispatchers' open screens (FR-222)
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public ApplySolveResult(
            PlanRepository plans,
            SolveJobRepository jobs,
            IdGenerator ids,
            Clock clock,
            LiveUpdates liveUpdates,
            AuditLog auditLog) {
        this.plans = plans;
        this.jobs = jobs;
        this.ids = ids;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Applies the job's result.
     *
     * <p>Built in M0.
     *
     * @param event the job's success
     * @param call the listener's server step, which the audit record names (ADR-110)
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code RouteLocked}, {@code
     *     OrderAlreadyInPlan} (BR-07), {@code VehicleAlreadyHasRoute} or {@code DriverAlreadyHasRoute}
     *     (BR-39); each means a check upstream was skipped, so the event registry retries and then
     *     alerts the team (DL §12)
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void on(SolveSucceeded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
