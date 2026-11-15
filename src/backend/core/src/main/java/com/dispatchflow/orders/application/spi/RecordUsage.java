package com.dispatchflow.orders.application.spi;

import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PlanId;
import java.util.List;

/**
 * Which plans not yet completed use a customer, which Customers &amp; Orders asks before it retires
 * one (BR-43, DL §4.3). Planning implements it as its {@code CustomerUsageService} (DL §2.1's
 * dashed arrow, placement P9). Customers &amp; Orders translates the answer into its {@code InUse}.
 *
 * <p>Built in M2.
 */
public interface RecordUsage {

    /**
     * The plans not yet completed that serve or drop an order of the customer.
     *
     * <p>Built in M2.
     *
     * @param customer the customer
     * @return the plans, each once, in any order; empty when none uses the customer
     */
    List<PlanId> plansUsing(CustomerId customer);
}
