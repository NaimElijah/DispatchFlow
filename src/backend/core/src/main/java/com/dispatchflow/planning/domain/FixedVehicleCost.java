package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.domain.PlanningError.NonPositiveFixedVehicleCost;
import com.dispatchflow.shared.RuleViolation;
import java.time.Duration;
import java.util.Objects;

/**
 * The cost a solve adds for each vehicle it uses, so it also reduces the vehicles on the road; off
 * unless the company enables it (FR-21, BR-09). The objective counts seconds, so the cost is a
 * duration: BR-126's F.
 */
public sealed interface FixedVehicleCost {

    /** No cost per vehicle. */
    record Off() implements FixedVehicleCost {}

    /**
     * A cost per vehicle used.
     *
     * @param cost the cost, above zero
     */
    record On(Duration cost) implements FixedVehicleCost {

        /**
         * Checks the cost.
         *
         * @throws RuleViolation with {@link NonPositiveFixedVehicleCost} when it is zero or less
         */
        public On {
            Objects.requireNonNull(cost, "cost");
            if (cost.isNegative() || cost.isZero()) {
                throw new RuleViolation(new NonPositiveFixedVehicleCost());
            }
        }
    }
}
