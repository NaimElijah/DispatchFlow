package com.dispatchflow.reporting.domain;

import com.dispatchflow.reporting.domain.ReportingError.OrderCountedTwice;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RuleViolation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every order of a plan's day, each once, with what the plan does with it: the denominator of the
 * planned on-time rate (FR-266).
 *
 * @param orders the orders, in the order given
 */
public record PlannedOrders(List<PlannedOrder> orders) {

    /**
     * Copies the orders and checks each appears once.
     *
     * @throws RuleViolation with {@link OrderCountedTwice} naming the first order given twice
     */
    public PlannedOrders {
        orders = List.copyOf(orders);
        Set<OrderId> seen = new HashSet<>();
        for (PlannedOrder order : orders) {
            if (!seen.add(order.order())) {
                throw new RuleViolation(new OrderCountedTwice(order.order()));
            }
        }
    }

    /**
     * Creates the orders.
     *
     * @param orders the orders of the day
     * @return the orders
     * @throws RuleViolation with {@link OrderCountedTwice} naming the first order given twice
     */
    public static PlannedOrders of(List<PlannedOrder> orders) {
        return new PlannedOrders(orders);
    }
}
