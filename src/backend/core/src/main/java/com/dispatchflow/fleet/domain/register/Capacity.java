package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * What a vehicle can carry: a weight and a volume, both required and positive (FR-05, BR-50,
 * ADR-061). The kernel's {@link Weight} and {@link Volume} check each one.
 *
 * <p>A vehicle embeds it in ADR-061's two columns, {@code weight_kg} and {@code volume_l}. The
 * constructor only checks both are present, and the kernel's converters rebuild each one, so
 * Hibernate's own record instantiator builds it (ADR-126).
 *
 * @param weight the most kilograms it carries
 * @param volume the most litres it carries
 */
@Embeddable
public record Capacity(
        @Column(name = "weight_kg") Weight weight,
        @Column(name = "volume_l") Volume volume) {

    /** Checks both are present. */
    public Capacity {
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(volume, "volume");
    }

    /**
     * Creates the capacity.
     *
     * @param weight the most kilograms it carries
     * @param volume the most litres it carries
     * @return the capacity
     */
    public static Capacity of(Weight weight, Volume volume) {
        return new Capacity(weight, volume);
    }
}
