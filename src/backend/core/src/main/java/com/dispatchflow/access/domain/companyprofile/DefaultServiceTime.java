package com.dispatchflow.access.domain.companyprofile;

import com.dispatchflow.access.domain.AccessError.NonPositiveDefaultServiceTime;
import com.dispatchflow.shared.RuleViolation;

/**
 * The service duration a new customer starts with, in whole seconds, which must be positive
 * (FR-184). Access models it itself, because its domain imports nothing from Customers & Orders,
 * which reads it through Access's API (DL §2.3 rule 1; ADR-104).
 *
 * @param seconds the duration
 */
public record DefaultServiceTime(long seconds) {

    /**
     * Checks the duration.
     *
     * @throws RuleViolation with {@link NonPositiveDefaultServiceTime} when it is zero or negative
     */
    public DefaultServiceTime {
        if (seconds <= 0) {
            throw new RuleViolation(new NonPositiveDefaultServiceTime(seconds));
        }
    }
}
