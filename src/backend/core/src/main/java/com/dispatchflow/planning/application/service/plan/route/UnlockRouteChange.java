package com.dispatchflow.planning.application.service.plan.route;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link UnlockRoute}: it loads the plan, checks its version, removes the route's
 * lock, saves the plan and writes the audit record (TS §6.2, ADR-063, ADR-129).
 *
 * <p>Built in an unscheduled milestone.
 */
@Transactional
public class UnlockRouteChange {

    private final PlanRepository plans;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept
     * @param clock the time, for {@code today}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public UnlockRouteChange(PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Unlocks the route.
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param route the locked route
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast}, {@code RouteNotInPlan}, or {@code RouteNotLocked}
     */
    public void apply(Actor actor, PlanId plan, RouteId route, Version expected) {
        throw new UnsupportedOperationException("Built in an unscheduled milestone");
    }
}
