package com.dispatchflow.reporting.domain;

import com.dispatchflow.reporting.domain.ReportingError.NegativeDistance;
import com.dispatchflow.shared.RuleViolation;

/**
 * A planned distance in whole metres, as the optimizer's legs give it (FR-38, FR-39, FR-341). It is
 * kept exact; only a screen converts it to kilometres and rounds (ADR-109).
 *
 * @param metres the distance, 0 or more
 */
public record Distance(long metres) {

    /**
     * Checks the distance.
     *
     * @throws RuleViolation with {@link NegativeDistance} when {@code metres} is below 0
     */
    public Distance {
        if (metres < 0) {
            throw new RuleViolation(new NegativeDistance(metres));
        }
    }

    /**
     * Creates the distance.
     *
     * @param metres the distance in metres
     * @return the distance
     * @throws RuleViolation with {@link NegativeDistance} when {@code metres} is below 0
     */
    public static Distance of(long metres) {
        return new Distance(metres);
    }
}
