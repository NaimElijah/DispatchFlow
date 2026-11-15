package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.util.Objects;

/**
 * What a vehicle can carry: a weight and a volume, both required and positive (FR-05, BR-50,
 * ADR-061). The kernel's {@link Weight} and {@link Volume} check each one.
 *
 * @param weight the most kilograms it carries
 * @param volume the most litres it carries
 */
public record Capacity(Weight weight, Volume volume) {

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
