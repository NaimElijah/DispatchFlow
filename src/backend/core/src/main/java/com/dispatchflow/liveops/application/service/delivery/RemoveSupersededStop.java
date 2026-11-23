package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.events.PutBackSuperseded;
import com.dispatchflow.orders.application.api.EndSupersededPutBack;
import com.dispatchflow.planning.application.api.RemoveSupersededPutBack;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Live Operations' own listener on {@code PutBackSuperseded}: a failed stop was corrected to
 * delivered after a put-back, so the put-back's stop is no longer needed. First Planning's {@link
 * RemoveSupersededPutBack} takes it off its plan with the cause {@code CORRECTED_AFTER_PUT_BACK}
 * (FR-404, FR-258, ADR-107, ADR-114). Then Customers &amp; Orders' {@link EndSupersededPutBack}
 * ends the order's put-back attempt, so the order goes back to its earlier attempt, derives
 * <em>delivered</em> from it, and no solve plans it again (ADR-121). Planning never listens to Live
 * Operations (DL §2.3 rule 2). {@link RemoveSupersededStopChange} holds the two steps, each in a
 * transaction of its own, so each changes one module's aggregate (ADR-063).
 *
 * <p>Delivery is at least once (ADR-064). Both calls are idempotent: a redelivery finds the stop
 * already off the plan and the order already back on its earlier attempt, and changes neither, so
 * it records no {@code ProcessedEvents} pair. A failure between the two leaves the stop off and the
 * attempt open, and the retried event finishes the second step.
 *
 * <p>Built in M8. Rows: FR-404.
 */
public final class RemoveSupersededStop {

    private final RemoveSupersededStopChange change;

    /**
     * Creates the listener's use case.
     *
     * @param change the listener's two transactions
     */
    public RemoveSupersededStop(RemoveSupersededStopChange change) {
        this.change = change;
    }

    /**
     * Asks Planning to take the put-back's stop off the plan of the date the event names, and then
     * Customers &amp; Orders to return the order to its earlier attempt.
     *
     * <p>Built in M8.
     *
     * @param event the superseded put-back
     * @param call the listener's server step, which both calls carry
     * @throws com.dispatchflow.shared.application.NotFound when no order has the event's order ID
     * @throws StaleVersion when Planning's plan or the order changed meanwhile; the listener adapter
     *     retries the event at once, up to three times (DL §11)
     */
    public void on(PutBackSuperseded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
