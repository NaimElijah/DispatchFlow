package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.PlanId;
import java.util.Comparator;
import java.util.List;

/**
 * The plans not yet completed that use a customer, as Planning's {@code RecordUsage} answers and
 * the application layer translates (BR-43, DL §4.3). A customer with any can't be retired.
 *
 * @param plans the plans, ordered by ID; empty when none uses it
 */
public record InUse(List<PlanId> plans) {

    /** No plan not yet completed uses the customer. */
    public static final InUse NONE = new InUse(List.of());

    /** Copies the plans in a fixed order. */
    public InUse {
        plans = plans.stream().sorted(Comparator.comparing(PlanId::value)).toList();
    }

    /**
     * Whether any plan uses the customer.
     *
     * @return {@code true} when there is at least one
     */
    public boolean isInUse() {
        return !plans.isEmpty();
    }
}
