package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.application.NotFound;

/**
 * The order details at a stop (DL §2.2): Live Operations judges a stop's report by them and shows
 * them to the driver (FR-27, FR-86, FR-87, FR-135, FR-366), and Planning's {@code RouteQueries}
 * prints them on the printable route and the loading list (FR-72, FR-153).
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
