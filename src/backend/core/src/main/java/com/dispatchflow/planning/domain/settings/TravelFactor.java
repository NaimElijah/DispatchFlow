package com.dispatchflow.planning.domain.settings;

import com.dispatchflow.planning.domain.PlanningError.NonPositiveTravelFactor;
import com.dispatchflow.shared.RuleViolation;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * The factor a travel bucket multiplies OSRM's base durations by (FR-321). It is applied only in the
 * Python {@code TravelTimeModel} (DL §10.2); Planning keeps the setting. It is held in its normal
 * form, so {@code 1.30} and {@code 1.3} are equal.
 *
 * @param value the factor, above zero
 */
public record TravelFactor(BigDecimal value) {

    /**
     * Checks the factor and normalizes it.
     *
     * @throws RuleViolation with {@link NonPositiveTravelFactor} when it is zero or less
     */
    public TravelFactor {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0) {
            throw new RuleViolation(new NonPositiveTravelFactor());
        }
        value = value.stripTrailingZeros();
    }

    /**
     * Creates the factor.
     *
     * @param value the factor, such as {@code new BigDecimal("1.3")}
     * @return the factor
     * @throws RuleViolation with {@link NonPositiveTravelFactor} when it is zero or less
     */
    public static TravelFactor of(BigDecimal value) {
        return new TravelFactor(value);
    }
}
