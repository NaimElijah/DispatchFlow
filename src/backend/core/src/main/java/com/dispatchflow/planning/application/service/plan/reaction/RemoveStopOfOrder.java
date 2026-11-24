package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.events.OrderCancelled;
import com.dispatchflow.orders.events.OrderRescheduled;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Takes a cancelled or rescheduled order's stop off the plan of its date at once, without optimizing
 * (FR-207, FR-315, FR-316, DL §6.2). It overrides a lock. On a ready plan the journal is cleared; on a
 * published plan it is a revision with the cause, and raises {@code PlanRevised}. The stop's route is
 * then <em>not yet checked</em>, for the re-check job (FR-395).
 *
 * <p>It is idempotent by nature: {@code Plan.removeStopOf} changes nothing for an order the plan
 * doesn't hold, or in any other state (ADR-101). It writes its audit record with the listener's server step (ADR-110).
 *
 * <p>{@link RemoveStopOfOrderChange} holds the transaction, which takes the stop off and writes the
 * audit record; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class RemoveStopOfOrder {

    private final LiveUpdates liveUpdates;
    private final RemoveStopOfOrderChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the removal's transaction
     */
    public RemoveStopOfOrder(LiveUpdates liveUpdates, RemoveStopOfOrderChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Takes a cancelled order's stop off its date's plan, with the cause {@code ORDER_CANCELLED}
     * (FR-207).
     *
     * <p>To Be Implemented.
     *
     * @param event the cancellation
     * @param call the listener's server step, which the audit record names (ADR-110)
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void on(OrderCancelled event, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Takes a rescheduled order's stop off its old date's plan, with the cause {@code
     * ORDER_RESCHEDULED} (FR-315). A reschedule within the same date changes no plan.
     *
     * <p>To Be Implemented.
     *
     * @param event the reschedule
     * @param call the listener's server step, which the audit record names (ADR-110)
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void on(OrderRescheduled event, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
