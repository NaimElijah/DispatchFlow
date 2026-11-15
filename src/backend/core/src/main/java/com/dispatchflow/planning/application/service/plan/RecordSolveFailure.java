package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.events.SolveFailed;
import com.dispatchflow.shared.application.Clock;

/**
 * Returns a plan whose daily solve failed to ready if it was solved before, and otherwise to draft,
 * with its routes as they were (BR-57, BR-59, FR-65, FR-222, FR-225). Its listener calls it on {@link
 * SolveFailed}. A failed re-optimization changes no plan (BR-26).
 *
 * <p>It is idempotent by nature: {@code Plan.solveFailed} ignores a job the plan no longer waits for,
 * an abandoned plan included (ADR-053). It writes its audit record with the listener's server step (ADR-110).
 *
 * <p>Built in M0.
 */
public final class RecordSolveFailure {

    private final PlanRepository plans;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept
     * @param clock the time, for the audit record
     * @param liveUpdates the dispatchers' open screens, so the failure shows without a reload (FR-65)
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public RecordSolveFailure(PlanRepository plans, Clock clock, LiveUpdates liveUpdates, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Records the failure on the plan.
     *
     * <p>Built in M0.
     *
     * @param event the job's failure
     * @param call the listener's server step, which the audit record names (ADR-110)
     */
    public void on(SolveFailed event, CallContext call) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
