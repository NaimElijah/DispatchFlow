package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Cancels a published plan for a day that doesn't run, while no route has started and no stop is
 * reported (FR-336 to FR-338, BR-103). In its transaction, {@link CancelPlanChange#apply} asks Live
 * Operations, through {@code ExecutionFacts.startedAmong} with the plan's own routes and stops, which
 * have started or are reported: an in-process call, not an outside one (DL §6.3). Its routes stay as
 * they were, and it makes no revision. It raises {@code PlanCancelled}, so the drivers are alerted
 * and the orders return to unplanned (FR-337).
 *
 * <p>{@link CancelPlanChange} holds the transaction, which cancels the plan and writes the audit
 * record; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M9.
 */
public final class CancelPlan {

    private final LiveUpdates liveUpdates;
    private final CancelPlanChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the cancellation's transaction
     */
    public CancelPlan(LiveUpdates liveUpdates, CancelPlanChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
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
