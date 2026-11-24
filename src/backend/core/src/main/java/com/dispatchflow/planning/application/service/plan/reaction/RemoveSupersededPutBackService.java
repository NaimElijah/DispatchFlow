package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.api.RemoveSupersededPutBack;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Planning's implementation of {@link RemoveSupersededPutBack}: it loads the date's uncancelled plan
 * and calls {@code Plan.removeStopOf} with the cause {@code CORRECTED_AFTER_PUT_BACK}, which changes
 * nothing when the stop is already gone (FR-404, ADR-114).
 *
 * <p>{@link RemoveSupersededPutBackServiceChange} holds the transaction, which takes the stop off and
 * writes the audit record; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class RemoveSupersededPutBackService implements RemoveSupersededPutBack {

    private final LiveUpdates liveUpdates;
    private final RemoveSupersededPutBackServiceChange change;

    /**
     * Creates the service.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the removal's transaction
     */
    public RemoveSupersededPutBackService(LiveUpdates liveUpdates, RemoveSupersededPutBackServiceChange change) {
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
    public void remove(OrderId order, Attempt laterAttempt, DeliveryDate date, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
