package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.events.OrderPutBack;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Takes a dropped order and attempt off the ready plan of its old date after it was put back onto
 * another date, so publishing isn't refused for an order its date no longer has (FR-75, FR-258,
 * FR-316, BR-71, ADR-112, ADR-115). Only a drop leaves; a plan in any other state keeps it.
 *
 * <p>It is idempotent by nature: {@code Plan.removePutBackOrder} changes nothing for an order the plan
 * doesn't drop (ADR-101). It writes its audit record with the listener's server step (ADR-110).
 *
 * <p>{@link RemovePutBackOrderChange} holds the transaction, which takes the drop off and writes the
 * audit record; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class RemovePutBackOrder {

    private final LiveUpdates liveUpdates;
    private final RemovePutBackOrderChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the removal's transaction
     */
    public RemovePutBackOrder(LiveUpdates liveUpdates, RemovePutBackOrderChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Takes the put-back attempt's drop off its old date's plan. A put-back onto the same date changes
     * no plan (DL §6.2).
     *
     * <p>To Be Implemented.
     *
     * @param event the put-back
     * @param call the listener's server step, which the audit record names (ADR-110)
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void on(OrderPutBack event, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
