package com.dispatchflow.orders.application.service.order;

import com.dispatchflow.orders.application.api.OrderChanges;
import com.dispatchflow.orders.application.api.OrderFact;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.orders.application.port.out.OrdersViews;
import com.dispatchflow.shared.DeliveryDate;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers &amp; Orders' implementation of {@link OrdersForDate}: it reads a date's orders from
 * its own read port, so Planning and Live Operations never read its schema (DL §2.3 rules 1 and 3,
 * BR-22).
 *
 * <p>{@link OrderFact#placedAt} has no source yet, because {@code Order} holds only its last change
 * time: the time the order joined its date, set by {@code place}, {@code reschedule} and a
 * put-back to another date (BR-71), is not recorded yet.
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class OrdersForDateService implements OrdersForDate {

    private final OrdersViews views;

    /**
     * Creates the service.
     *
     * @param views Customers &amp; Orders' read port
     */
    public OrdersForDateService(OrdersViews views) {
        this.views = views;
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public List<OrderFact> uncancelledOn(DeliveryDate date) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public OrderChanges changesSince(DeliveryDate date, Instant since) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
