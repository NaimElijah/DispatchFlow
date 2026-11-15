package com.dispatchflow.planning.application.api;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;

/**
 * Takes a put-back's stop off its plan after the failed stop before it was corrected to delivered
 * (FR-404, FR-258, ADR-107, ADR-114). Live Operations' own listener on {@code PutBackSuperseded}
 * calls it with the event's fields, so Planning never listens to Live Operations.
 *
 * <p>Built in M8.
 */
public interface RemoveSupersededPutBack {

    /**
     * Finds the plan of the date and takes the attempt's stop off it with the cause {@code
     * CORRECTED_AFTER_PUT_BACK}. It is idempotent: when the stop is already gone, or the date has no
     * plan, nothing changes, so a retried listener is safe (ADR-114).
     *
     * <p>Built in M8.
     *
     * @param order the corrected stop's order
     * @param laterAttempt the put-back's attempt, whose stop leaves
     * @param date the delivery date the put-back's stop is on
     * @param call the listener's server step
     * @throws com.dispatchflow.shared.application.StaleVersion when the plan changed between its read
     *     and its save (BR-76); the caller's listener or job adapter retries at once, up to three
     *     times (DL §11)
     */
    void remove(OrderId order, Attempt laterAttempt, DeliveryDate date, CallContext call);
}
