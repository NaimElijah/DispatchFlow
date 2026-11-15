package com.dispatchflow.orders.application.service;

import com.dispatchflow.orders.application.api.RecipientContactFact;
import com.dispatchflow.orders.application.api.RecipientContacts;
import com.dispatchflow.orders.application.port.out.OrdersViews;
import com.dispatchflow.shared.OrderId;

/**
 * Customers &amp; Orders' implementation of {@link RecipientContacts}: it reads an order's
 * recipient phone and its customer's contact phone from its own read port, for Messaging to choose
 * from (DL §2.2, FR-348).
 *
 * <p>Built in M12.
 */
public final class RecipientContactsService implements RecipientContacts {

    private final OrdersViews views;

    /**
     * Creates the service.
     *
     * @param views Customers &amp; Orders' read port
     */
    public RecipientContactsService(OrdersViews views) {
        this.views = views;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M12.
     */
    @Override
    public RecipientContactFact of(OrderId order) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
