package com.dispatchflow.orders.application.api;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;

/**
 * Ends an order's put-back attempt after the failed stop before it was corrected to delivered
 * (FR-404, FR-258, ADR-121). The order goes back to its earlier attempt, on that attempt's date
 * and window, which the put-back kept, so its status derives <em>delivered</em> from that attempt's
 * {@code Delivery}, and no solve plans it again. Live
 * Operations' own listener on {@code PutBackSuperseded} calls it after Planning's {@code
 * RemoveSupersededPutBack} has taken the put-back's stop off its plan (DL §2.1).
 *
 * <p>Built in M8.
 */
public interface EndSupersededPutBack {

    /**
     * Returns the order from its later attempt to the attempt before it. It is idempotent: when the
     * order's current attempt is no longer {@code laterAttempt}, nothing changes, so a retried
     * listener is safe.
     *
     * <p>Built in M8.
     *
     * @param order the corrected stop's order
     * @param laterAttempt the put-back's attempt, which ends
     * @param call the listener's server step, which the audit record carries
     * @throws com.dispatchflow.shared.application.NotFound when no order has the ID
     * @throws com.dispatchflow.shared.application.StaleVersion when the order changed between its
     *     read and its save (BR-76); the caller's listener adapter retries at once, up to three
     *     times (DL §11)
     */
    void end(OrderId order, Attempt laterAttempt, CallContext call);
}
