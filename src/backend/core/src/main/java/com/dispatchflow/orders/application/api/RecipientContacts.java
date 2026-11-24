package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.application.NotFound;
import java.util.List;

/**
 * The phones stops' recipients can be messaged at, which Messaging asks before it sends (DL §2.2,
 * FR-348). It answers many orders in one read, so a route's or a day's stops cost one call.
 *
 * <p>To Be Implemented.
 */
public interface RecipientContacts {

    /**
     * Each order's recipient phone and its customer's contact phone.
     *
     * <p>To Be Implemented.
     *
     * @param orders the orders, each once; one order for a single stop
     * @return one answer per order, in the order asked, each with both phones where they exist
     * @throws NotFound when any of the IDs names no order
     */
    List<RecipientContactFact> of(List<OrderId> orders);
}
