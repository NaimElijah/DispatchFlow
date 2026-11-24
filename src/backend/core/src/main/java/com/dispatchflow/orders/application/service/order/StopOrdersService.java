package com.dispatchflow.orders.application.service.order;

import com.dispatchflow.orders.application.api.StopOrderFact;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.orders.application.port.out.OrdersViews;
import com.dispatchflow.shared.OrderId;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers &amp; Orders' implementation of {@link StopOrders}: it reads the orders' door flags and
 * the fields a driver sees at their stops from its own read port in one read, and refuses the
 * request when any order is missing from the answer (DL §2.2, FR-86, FR-87, FR-135, FR-364).
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class StopOrdersService implements StopOrders {

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
     * <p>To Be Implemented.
     */
    @Override
    public List<StopOrderFact> of(List<OrderId> orders) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
