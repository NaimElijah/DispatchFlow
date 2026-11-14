package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.Measure;
import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity;
import com.dispatchflow.shared.SharedKernelError.QuantityOutOfRange;
import java.math.BigDecimal;

/**
 * A positive volume in litres: a vehicle's capacity or an order's demand (BR-50). It fits the
 * {@code volume_l numeric(9,2)} column (ADR-061): at most 7 integer digits and 2 decimals.
 *
 * <p>It is a {@link BigDecimal} for the same reason as {@link Weight}: an entered decimal is kept
 * exactly. It is held in its normal form, so {@code 2.0} and {@code 2} are equal.
 *
 * @param litres the volume
 */
public record Volume(BigDecimal litres) implements Comparable<Volume> {

    private static final int INTEGER_DIGITS = 7;

    /**
     * Checks the volume and normalizes it.
     *
     * @throws RuleViolation with {@link NonPositiveQuantity} when the volume is zero or negative,
     *     or with {@link QuantityOutOfRange} when its column can't hold it
     */
    public Volume {
        litres = Quantities.normalized(litres, INTEGER_DIGITS, Measure.VOLUME);
    }

    /**
     * Creates the volume.
     *
     * @param litres the volume in litres
     * @return the volume
     * @throws RuleViolation with {@link NonPositiveQuantity} when the volume is zero or negative,
     *     or with {@link QuantityOutOfRange} when its column can't hold it
     */
    public static Volume litres(BigDecimal litres) {
        return new Volume(litres);
    }

    @Override
    public int compareTo(Volume other) {
        return litres.compareTo(other.litres);
    }
}
