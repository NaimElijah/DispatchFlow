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
     * The most decimals accepted before normalizing. Trailing zeros make an exact value longer, so
     * {@code 1.000…0} with many zeros is still 1; this bounds the work of stripping them, which
     * takes seconds on a number thousands of digits long.
     */
    private static final int MAX_INPUT_SCALE = 100;

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
        // Integer digits first, in long arithmetic: a scale of Integer.MIN_VALUE must not overflow.
        // It also refuses a huge exponent such as 1E+1000000000 before anything expands it.
        long integerDigits = (long) value.precision() - value.scale();
        if (integerDigits > maxIntegerDigits || value.scale() > MAX_INPUT_SCALE) {
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
