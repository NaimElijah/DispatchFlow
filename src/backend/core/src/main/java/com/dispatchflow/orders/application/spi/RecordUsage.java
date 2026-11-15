package com.dispatchflow.orders.application.spi;

import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import java.util.List;

/**
 * Which plans not yet completed use a customer, which Customers &amp; Orders asks before it retires
 * one (BR-43, DL §4.3). Planning implements it as its {@code CustomerUsageService} (DL §2.1's
 * dashed arrow, placement P9). Customers &amp; Orders translates the answer into its {@code InUse}.
 *
 * <p>The question names the customer's orders, not the customer: Customers &amp; Orders knows which
 * orders are a customer's, and a plan holds orders, so Planning never needs to know customers.
 *
 * <p>Built in M2.
 */
public interface RecordUsage {

    /**
     * The plans not yet completed that serve or drop any of the given orders.
     *
     * <p>Built in M2.
     *
     * @param orders the customer's uncancelled orders dated today or later, since a plan of an
     *     earlier date is completed or abandoned (BR-102)
     * @return the plans, each once, in any order; empty when none uses an order
     */
    List<PlanId> plansUsing(List<OrderId> orders);
}
