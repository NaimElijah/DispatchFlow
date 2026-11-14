package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity;
import com.dispatchflow.shared.SharedKernelError.NonPositiveQuantity.Measure;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A positive volume in litres: a vehicle's capacity or an order's demand (BR-50).
 *
 * <p>It is a {@link BigDecimal} for the same reason as {@link Weight}: an entered decimal is kept
 * exactly. Trailing zeros are dropped, so {@code 2.0} and {@code 2} are equal.
 *
 * @param litres the volume
 */
public record Volume(BigDecimal litres) {

    /**
     * Checks the volume and normalizes its scale.
     *
     * @throws RuleViolation with {@link NonPositiveQuantity} when the volume is zero or negative
     */
    public Volume {
        Objects.requireNonNull(litres, "litres");
        if (litres.signum() <= 0) {
            throw new RuleViolation(new NonPositiveQuantity(Measure.VOLUME));
        }
        litres = litres.stripTrailingZeros();
    }

    /**
     * Creates the volume.
     *
     * @param litres the volume in litres
     * @return the volume
     * @throws RuleViolation with {@link NonPositiveQuantity} when the volume is zero or negative
     */
    public static Volume litres(BigDecimal litres) {
        return new Volume(litres);
    }
}
