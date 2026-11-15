package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.application.NotFound;

/**
 * The phones a stop's recipient can be messaged at, which Messaging asks before it sends (DL §2.2,
 * FR-348).
 *
 * <p>Built in M12.
 */
public interface RecipientContacts {

    /**
     * An order's recipient phone and its customer's contact phone.
     *
     * <p>Built in M12.
     *
     * @param order the order
     * @return both phones, each if it exists
     * @throws NotFound when no order has the ID
     */
    RecipientContactFact of(OrderId order);
}
