package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.Measure;
import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity;
import com.dispatchflow.shared.SharedKernelError.QuantityOutOfRange;
import java.math.BigDecimal;

/**
 * A positive weight in kilograms: a vehicle's capacity or an order's demand (BR-50). It fits the
 * {@code weight_kg numeric(8,2)} column (ADR-061): at most 6 integer digits and 2 decimals.
 *
 * <p>It is a {@link BigDecimal} so a typed or imported decimal such as {@code 12.3} is kept
 * exactly, where a {@code double} would round it. It is held in its normal form, so {@code 1.50}
 * and {@code 1.5} are equal.
 *
 * @param kilograms the weight
 */
public record Weight(BigDecimal kilograms) implements Comparable<Weight> {

    private static final int INTEGER_DIGITS = 6;

    /**
     * Checks the weight and normalizes it.
     *
     * @throws RuleViolation with {@link NonPositiveQuantity} when the weight is zero or negative,
     *     or with {@link QuantityOutOfRange} when its column can't hold it
     */
    public Weight {
        kilograms = Quantities.normalized(kilograms, INTEGER_DIGITS, Measure.WEIGHT);
    }

    /**
     * Creates the weight.
     *
     * @param kilograms the weight in kilograms
     * @return the weight
     * @throws RuleViolation with {@link NonPositiveQuantity} when the weight is zero or negative,
     *     or with {@link QuantityOutOfRange} when its column can't hold it
     */
    public static Weight kilograms(BigDecimal kilograms) {
        return new Weight(kilograms);
    }

    @Override
    public int compareTo(Weight other) {
        return kilograms.compareTo(other.kilograms);
    }
}
