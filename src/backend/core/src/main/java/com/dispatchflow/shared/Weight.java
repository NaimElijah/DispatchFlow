package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity;
import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity.Measure;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A positive weight in kilograms: a vehicle's capacity or an order's demand (BR-50).
 *
 * <p>It is a {@link BigDecimal} so a typed or imported decimal such as {@code 12.3} is kept
 * exactly, where a {@code double} would round it. Trailing zeros are dropped, so {@code 1.50} and
 * {@code 1.5} are equal.
 *
 * @param kilograms the weight
 */
public record Weight(BigDecimal kilograms) {

    /**
     * Checks the weight and normalizes its scale.
     *
     * @throws RuleViolation with {@link NonPositiveQuantity} when the weight is zero or negative
     */
    public Weight {
        Objects.requireNonNull(kilograms, "kilograms");
        if (kilograms.signum() <= 0) {
            throw new RuleViolation(new NonPositiveQuantity(Measure.WEIGHT));
        }
        kilograms = kilograms.stripTrailingZeros();
    }

    /**
     * Creates the weight.
     *
     * @param kilograms the weight in kilograms
     * @return the weight
     * @throws RuleViolation with {@link NonPositiveQuantity} when the weight is zero or negative
     */
    public static Weight kilograms(BigDecimal kilograms) {
        return new Weight(kilograms);
    }
}
