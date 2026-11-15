package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.application.NotFound;
import java.util.List;

/**
 * The order details at stops (DL §2.2): Live Operations judges a stop's report by them and shows
 * them to the driver (FR-27, FR-86, FR-87, FR-135, FR-366), and Planning's {@code RouteQueries}
 * prints them on the printable route (FR-72). It answers many orders
 * in one read, so a driver's day costs one call.
 *
 * <p>Built in M5.
 */
public interface StopOrders {

    /**
     * Each order's door flag and the fields a driver sees at its stop.
     *
     * <p>Built in M5.
     *
     * @param orders the stops' orders, each once; one order for a single stop's report
     * @return one answer per order, in the order asked, read now
     * @throws NotFound when any of the IDs names no order
     */
    List<StopOrderFact> of(List<OrderId> orders);
}
