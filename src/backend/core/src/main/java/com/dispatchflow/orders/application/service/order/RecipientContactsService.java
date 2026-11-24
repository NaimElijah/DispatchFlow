package com.dispatchflow.orders.application.service.order;

import com.dispatchflow.orders.application.api.RecipientContactFact;
import com.dispatchflow.orders.application.api.RecipientContacts;
import com.dispatchflow.orders.application.port.out.OrdersViews;
import com.dispatchflow.shared.OrderId;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers &amp; Orders' implementation of {@link RecipientContacts}: it reads the orders'
 * recipient phones and their customers' contact phones from its own read port in one read, for
 * Messaging to choose from, and refuses the request when any order is missing from the answer (DL
 * §2.2, FR-348).
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class RecipientContactsService implements RecipientContacts {

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
     * <p>To Be Implemented.
     */
    @Override
    public List<RecipientContactFact> of(List<OrderId> orders) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
