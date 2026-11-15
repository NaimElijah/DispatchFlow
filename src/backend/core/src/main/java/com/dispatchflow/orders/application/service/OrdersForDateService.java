package com.dispatchflow.orders.application.service;

import com.dispatchflow.orders.application.api.OrderChanges;
import com.dispatchflow.orders.application.api.OrderFact;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.orders.application.port.out.OrdersViews;
import com.dispatchflow.shared.DeliveryDate;
import java.time.Instant;
import java.util.List;

/**
 * Customers &amp; Orders' implementation of {@link OrdersForDate}: it reads a date's orders from
 * its own read port, so Planning and Live Operations never read its schema (DL §2.3 rules 1 and 3,
 * BR-22).
 *
 * <p>{@link OrderFact#placedAt} has no source yet, because {@code Order} holds only its last change
 * time: M2 adds the time the order joined its date, set by {@code place}, {@code reschedule} and a
 * put-back to another date (BR-71).
 *
 * <p>Built in M2 and M9, method by method.
 */
public final class OrdersForDateService implements OrdersForDate {

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
     * <p>Built in M2.
     */
    @Override
    public List<OrderFact> uncancelledOn(DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M9.
     */
    @Override
    public OrderChanges changesSince(DeliveryDate date, Instant since) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
