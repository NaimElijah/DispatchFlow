package com.dispatchflow.orders.application.service.order;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.api.EndSupersededPutBack;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers &amp; Orders' implementation of {@link EndSupersededPutBack}: once a failed stop was
 * corrected to delivered after its order was put back, it returns the order from the put-back's
 * attempt to the earlier one, so the order's status derives <em>delivered</em> from that attempt's
 * {@code Delivery} and no solve plans it again (FR-404, FR-258, ADR-121). An order whose current
 * attempt is no longer the put-back's changes nothing, so a redelivered event is safe. It writes
 * its audit record with the listener's server step (NFR-23, ADR-110).
 *
 * <p>Domain gap: {@code Order} has no method that ends a put-back attempt, and a put-back doesn't
 * keep the earlier attempt's date and window. Neither is built yet (ADR-121): {@code
 * Order.putBack} keeps them, and {@code Order.endPutBack(Attempt, Instant)} returns the order to
 * the attempt before the one given, on that attempt's date and window. It frees the later date's
 * capacity (BR-101), skips the past-date check, and changes nothing when the order's current
 * attempt is no longer that one.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class EndSupersededPutBackService implements EndSupersededPutBack {

    private final OrderRepository orders;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the service.
     *
     * @param orders where orders are kept
     * @param clock the time of the change
     * @param auditLog the audit trail
     */
    public EndSupersededPutBackService(OrderRepository orders, Clock clock, AuditLog auditLog) {
        this.orders = orders;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     *
     * @throws NotFound when no order has the ID
     * @throws StaleVersion when the order changed between its read and its save (BR-76); Live
     *     Operations' listener adapter retries at once, up to three times (DL §11)
     */
    @Override
    public void end(OrderId order, Attempt laterAttempt, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
