package com.dispatchflow.planning.application.api;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.NotFound;

/**
 * Puts a stop on the route that actually served it, after an accepted outcome arrived late (DL
 * §8, BR-89, BR-130, ADR-101). Live Operations' listener on {@code OutcomeRecorded} calls it when
 * the current revision disagrees with the fact, then raises the dispatcher's alert from the answer.
 *
 * <p>Built in M8.
 */
public interface ReconcileLateOutcome {

    /**
     * Reconciles the plan with the fact. It is idempotent: when the stop is already where the fact
     * puts it, nothing changes and the answer is {@link ReconciliationFact.Kind#UNCHANGED}, so a
     * retried listener is safe (DL §8).
     *
     * <p>Built in M8.
     *
     * @param fact the accepted outcome and the route that served it
     * @param call the listener's server step
     * @return what changed, for the dispatcher's alert
     * @throws NotFound when no plan holds the stop
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished} for a
     *     plan never published, or {@code RouteNotInPlan} for an unknown serving route
     * @throws com.dispatchflow.shared.application.StaleVersion when the plan changed between its read
     *     and its save (BR-76); the caller's listener or job adapter retries at once, up to three
     *     times (DL §11)
     */
    ReconciliationFact reconcile(LateOutcomeFact fact, CallContext call);
}
