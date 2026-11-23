package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.events.PutBackSuperseded;
import com.dispatchflow.orders.application.api.EndSupersededPutBack;
import com.dispatchflow.planning.application.api.RemoveSupersededPutBack;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two transactions of {@link RemoveSupersededStop} (FR-404, ADR-121): each call is one
 * transaction of its own, so Planning's plan and Customers &amp; Orders' order each change in their
 * own (ADR-063), and a failure between the two leaves the first one committed for the retried event
 * to build on.
 *
 * <p>Built in M8.
 */
@Transactional
public class RemoveSupersededStopChange {

    private final RemoveSupersededPutBack removeSupersededPutBack;
    private final EndSupersededPutBack endSupersededPutBack;

    /**
     * Creates the transactions.
     *
     * @param removeSupersededPutBack Planning's removal of a superseded put-back's stop
     * @param endSupersededPutBack Customers &amp; Orders' end of the order's put-back attempt
     */
    public RemoveSupersededStopChange(
            RemoveSupersededPutBack removeSupersededPutBack, EndSupersededPutBack endSupersededPutBack) {
        this.removeSupersededPutBack = removeSupersededPutBack;
        this.endSupersededPutBack = endSupersededPutBack;
    }

    /**
     * Asks Planning to take the put-back's stop off the plan of the date the event names, with the
     * cause {@code CORRECTED_AFTER_PUT_BACK}. A stop already off changes nothing.
     *
     * <p>Built in M8.
     *
     * @param event the superseded put-back
     * @param call the listener's server step, which Planning's call carries
     * @throws StaleVersion when Planning's plan changed meanwhile
     */
    public void removeStop(PutBackSuperseded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }

    /**
     * Asks Customers &amp; Orders to end the order's put-back attempt, so the order goes back to
     * its earlier attempt. An order already back changes nothing.
     *
     * <p>Built in M8.
     *
     * @param event the superseded put-back
     * @param call the listener's server step, which Customers &amp; Orders' call carries
     * @throws NotFound when no order has the event's order ID
     * @throws StaleVersion when the order changed meanwhile
     */
    public void endPutBack(PutBackSuperseded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
