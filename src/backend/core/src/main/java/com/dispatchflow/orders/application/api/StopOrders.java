package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.application.NotFound;

/**
 * The order details Live Operations judges a stop's report by and shows the driver (DL §2.2,
 * FR-27, FR-86, FR-87, FR-135, FR-366).
 *
 * <p>Built in M5.
 */
public interface StopOrders {

    /**
     * One order's door flag and the fields a driver sees at its stop.
     *
     * <p>Built in M5.
     *
     * @param order the stop's order
     * @return the order's stop fields, read now
     * @throws NotFound when no order has the ID
     */
    StopOrderFact of(OrderId order);
}
