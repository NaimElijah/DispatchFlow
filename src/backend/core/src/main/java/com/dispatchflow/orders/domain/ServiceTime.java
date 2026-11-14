package com.dispatchflow.orders.domain;

import com.dispatchflow.orders.domain.OrdersError.NonPositiveServiceTime;
import com.dispatchflow.shared.RuleViolation;

/**
 * How long a stop occupies its vehicle, in whole seconds (BR-04): positive (BR-35, BR-124). An order
 * given none takes its customer's default ({@link OrderDefaults}).
 *
 * @param seconds the duration, 1 or more
 */
public record ServiceTime(long seconds) {

    /**
     * Checks the duration.
     *
     * @throws RuleViolation with {@link NonPositiveServiceTime} when it is zero or negative
     */
    public ServiceTime {
        if (seconds <= 0) {
            throw new RuleViolation(new NonPositiveServiceTime(seconds));
        }
    }

    /**
     * Creates the duration.
     *
     * @param seconds the duration in seconds
     * @return the service time
     * @throws RuleViolation with {@link NonPositiveServiceTime} when it is zero or negative
     */
    public static ServiceTime ofSeconds(long seconds) {
        return new ServiceTime(seconds);
    }
}
