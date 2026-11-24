package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.api.LateOutcomeFact;
import com.dispatchflow.planning.application.api.ReconcileLateOutcome;
import com.dispatchflow.planning.application.api.ReconciliationFact;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Planning's implementation of {@link ReconcileLateOutcome}: it finds the plan that holds or held the
 * stop, translates the fact into Planning's {@code LateFact}, and calls {@code
 * Plan.reconcileLateOutcome}, which makes a new revision when the stop moves (DL §8, BR-89, BR-130,
 * ADR-101). It doesn't retry a refused save: Live Operations' listener adapter retries the whole
 * call, so the answer is the plan's as it then is (DL §11).
 *
 * <p>{@link ReconcileLateOutcomeServiceChange} holds the transaction, which reconciles the plan and
 * writes the audit record; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class ReconcileLateOutcomeService implements ReconcileLateOutcome {

    private final LiveUpdates liveUpdates;
    private final ReconcileLateOutcomeServiceChange change;

    /**
     * Creates the service.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the reconciliation's transaction
     */
    public ReconcileLateOutcomeService(LiveUpdates liveUpdates, ReconcileLateOutcomeServiceChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     *
     * @throws StaleVersion when the plan changed between its read and its save; Live Operations'
     *     listener adapter retries at once, up to three times (DL §11)
     */
    @Override
    public ReconciliationFact reconcile(LateOutcomeFact fact, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
