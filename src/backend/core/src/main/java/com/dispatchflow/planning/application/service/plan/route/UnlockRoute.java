package com.dispatchflow.planning.application.service.plan.route;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Removes a route's lock in a ready plan (FR-147, FR-239). It is kept unscheduled with {@link
 * LockRoute}, as an exception to ADR-120's P11 (ADR-121).
 *
 * <p>{@link UnlockRouteChange} holds the transaction, which removes the lock and writes the audit
 * record; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in an unscheduled milestone.
 */
public final class UnlockRoute {

    private final LiveUpdates liveUpdates;
    private final UnlockRouteChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the unlock's transaction
     */
    public UnlockRoute(LiveUpdates liveUpdates, UnlockRouteChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
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
    public void unlock(Actor actor, PlanId plan, RouteId route, Version expected) {
        throw new UnsupportedOperationException("Built in an unscheduled milestone");
    }
}
