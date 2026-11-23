package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.events.SolveSucceeded;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link ApplySolveResult}: it loads the plan and the job, applies the job's result
 * with {@code Plan.applySolveResult}, saves the plan and writes the audit record (TS §6.2, ADR-063,
 * ADR-129).
 *
 * <p>Built in M0.
 */
@Transactional
public class ApplySolveResultChange {

    private final PlanRepository plans;
    private final SolveJobRepository jobs;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept
     * @param jobs where solve jobs are kept, for the job's result
     * @param ids new IDs for the result's new routes and stops: one per route and one per stop
     *     (ADR-112)
     * @param clock the time, for {@code now}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public ApplySolveResultChange(
            PlanRepository plans, SolveJobRepository jobs, IdGenerator ids, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.jobs = jobs;
        this.ids = ids;
        this.clock = clock;
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
     *     (BR-39); each means a check upstream was skipped
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void apply(SolveSucceeded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
