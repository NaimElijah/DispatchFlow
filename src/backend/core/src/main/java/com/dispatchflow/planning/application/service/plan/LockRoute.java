package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.domain.plan.RouteLock;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Locks a route of a ready plan, or the sequence of its stops, against edits and the next daily solve
 * (FR-147, FR-238, FR-239). A lock changes no route, so it needs no evaluation; publishing clears
 * every lock (ADR-112).
 *
 * <p>Built in M4.
 */
public final class LockRoute {

    private final PlanRepository plans;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept
     * @param clock the time, for {@code today}
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public LockRoute(PlanRepository plans, Clock clock, LiveUpdates liveUpdates, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Locks the route, replacing any lock it had.
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param route the route
     * @param lock the kind of lock
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast}, or {@code RouteNotInPlan}
     */
    public void lock(Actor actor, PlanId plan, RouteId route, RouteLock lock, Version expected) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
