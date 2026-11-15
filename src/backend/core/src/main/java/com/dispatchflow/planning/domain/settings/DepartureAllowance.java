package com.dispatchflow.planning.domain.settings;

import com.dispatchflow.planning.domain.PlanningError.NegativeDepartureAllowance;
import com.dispatchflow.shared.RuleViolation;

/**
 * The minutes between a shift's start and leaving the depot, for loading; planned arrivals include
 * it, and so does the route-not-started alert (FR-67, FR-226, FR-101). It starts at 0 (ADR-101).
 *
 * @param minutes the allowance, in whole minutes, zero or more
 */
public record DepartureAllowance(int minutes) {

    /** The allowance until an Admin sets another: none (ADR-101). */
    public static final DepartureAllowance STARTING = new DepartureAllowance(0);

    /**
     * Checks the allowance.
     *
     * @throws RuleViolation with {@link NegativeDepartureAllowance} when it is below zero
     */
    public DepartureAllowance {
        if (minutes < 0) {
            throw new RuleViolation(new NegativeDepartureAllowance());
        }
    }

    /**
     * Creates the allowance.
     *
     * @param minutes the allowance in whole minutes
     * @return the allowance
     * @throws RuleViolation with {@link NegativeDepartureAllowance} when it is below zero
     */
    public static DepartureAllowance ofMinutes(int minutes) {
        return new DepartureAllowance(minutes);
    }
}
