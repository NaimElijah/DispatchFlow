package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.spi.ExecutionFacts;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Cancels a published plan for a day that doesn't run, while no route has started and no stop is
 * reported (FR-336 to FR-338, BR-103). Before its transaction it asks Live Operations, through {@code
 * ExecutionFacts.startedAmong} with the plan's own routes and stops, which have started or are
 * reported. Its routes stay as they were, and it makes no revision. It raises {@code PlanCancelled},
 * so the drivers are alerted and the orders return to unplanned (FR-337).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M9.
 */
public final class CancelPlan {

    private final PlanRepository plans;
    private final ExecutionFacts executionFacts;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept
     * @param executionFacts Live Operations' started routes and reported stops, among the plan's own
     * @param clock the time, for {@code today} and {@code now}
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public CancelPlan(
            PlanRepository plans,
            ExecutionFacts executionFacts,
            Clock clock,
            LiveUpdates liveUpdates,
            AuditLog auditLog) {
        this.plans = plans;
        this.executionFacts = executionFacts;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Cancels the plan.
     *
     * <p>Built in M9.
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
    public void cancel(Actor actor, PlanId plan, Version expected) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
