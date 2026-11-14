package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.Measure;
import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity;
import com.dispatchflow.shared.SharedKernelError.QuantityOutOfRange;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * The checks {@link Weight} and {@link Volume} share: positive, and within their column (ADR-061).
 */
final class Quantities {

    /**
     * Far above any value a {@code numeric(9,2)} column holds. Input past it is refused before
     * normalizing, because stripping zeros from a number with a huge precision or exponent takes
     * seconds.
     */
    private static final int MAX_INPUT_DIGITS = 20;

    private static final int DECIMALS = 2;

    private Quantities() {}

    /**
     * Checks a quantity and returns its normal form: no trailing zeros, and never a negative
     * scale, so {@code 100} prints as {@code 100} and not {@code 1E+2}.
     *
     * @param value the entered quantity
     * @param maxIntegerDigits the integer digits its column holds
     * @param measure what the quantity measures, for the error
     * @return the normalized quantity
     * @throws RuleViolation with {@link QuantityOutOfRange} when the column can't hold it, or with
     *     {@link NonPositiveQuantity} when it is zero or negative
     */
    static BigDecimal normalized(BigDecimal value, int maxIntegerDigits, Measure measure) {
        Objects.requireNonNull(value, "value");
        int scale = value.scale();
        if (value.precision() > MAX_INPUT_DIGITS || scale > MAX_INPUT_DIGITS || scale < -MAX_INPUT_DIGITS) {
            throw new RuleViolation(new QuantityOutOfRange(measure));
        }
        if (value.signum() <= 0) {
            throw new RuleViolation(new NonPositiveQuantity(measure));
        }
        BigDecimal normal = value.stripTrailingZeros();
        if (normal.scale() < 0) {
            normal = normal.setScale(0);
        }
        if (normal.scale() > DECIMALS || normal.precision() - normal.scale() > maxIntegerDigits) {
            throw new RuleViolation(new QuantityOutOfRange(measure));
        }
        return normal;
    }
}
