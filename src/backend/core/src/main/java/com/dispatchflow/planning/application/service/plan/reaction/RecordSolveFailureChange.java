package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.events.SolveFailed;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RecordSolveFailure}: it loads the plan, records the failure with {@code
 * Plan.solveFailed}, saves the plan and writes the audit record (TS §6.2, ADR-063, ADR-129).
 *
 * <p>Built in M0.
 */
@Transactional
public class RecordSolveFailureChange {

    private final PlanRepository plans;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept
     * @param clock the time, for the audit record
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public RecordSolveFailureChange(PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Records the failure on the plan.
     *
     * <p>Built in M0.
     *
     * @param event the job's failure
     * @param call the listener's server step, which the audit record names (ADR-110)
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void apply(SolveFailed event, CallContext call) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
