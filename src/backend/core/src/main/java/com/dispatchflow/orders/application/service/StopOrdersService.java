package com.dispatchflow.orders.application.service;

import com.dispatchflow.orders.application.api.StopOrderFact;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.orders.application.port.out.OrdersViews;
import com.dispatchflow.shared.OrderId;

/**
 * Customers &amp; Orders' implementation of {@link StopOrders}: it reads an order's door flag and
 * the fields a driver sees at its stop from its own read port (DL §2.2, FR-86, FR-87, FR-135,
 * FR-364).
 *
 * <p>Built in M5.
 */
public final class StopOrdersService implements StopOrders {

    private final OrdersViews views;

    /**
     * Creates the service.
     *
     * @param views Customers &amp; Orders' read port
     */
    public StopOrdersService(OrdersViews views) {
        this.views = views;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M5.
     */
    @Override
    public StopOrderFact of(OrderId order) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
