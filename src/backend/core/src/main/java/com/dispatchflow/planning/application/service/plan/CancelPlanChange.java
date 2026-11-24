package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.spi.ExecutionFacts;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link CancelPlan}: it loads the plan, checks its version, asks Live Operations
 * which of the plan's routes have started and which stops are reported, cancels the plan with {@code
 * Plan.cancel}, saves it, which raises {@code PlanCancelled}, and writes the audit record (TS §6.2,
 * ADR-063, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class CancelPlanChange {

    private final PlanRepository plans;
    private final ExecutionFacts executionFacts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept
     * @param executionFacts Live Operations' started routes and reported stops, among the plan's own
     * @param clock the time, for {@code today} and {@code now}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public CancelPlanChange(PlanRepository plans, ExecutionFacts executionFacts, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.executionFacts = executionFacts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Cancels the plan.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished}, {@code
     *     DateInPast}, {@code RouteAlreadyStarted} naming every started route, or {@code
     *     StopAlreadyReported} naming every reported stop
     */
    public void apply(Actor actor, PlanId plan, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
