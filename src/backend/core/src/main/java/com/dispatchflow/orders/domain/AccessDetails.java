package com.dispatchflow.orders.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * How the driver reaches the door: an order's floor, apartment and entrance code, which the driver
 * sees at the stop (FR-86). Each part is optional. With the address they locate and open a home, so
 * they are personal data, and each part hides itself when printed.
 *
 * @param floor the floor, if given
 * @param apartment the apartment, if given
 * @param entranceCode the entrance code, if given
 */
public record AccessDetails(Optional<Floor> floor, Optional<Apartment> apartment, Optional<EntranceCode> entranceCode) {

    /** No floor, apartment or entrance code. */
    public static final AccessDetails NONE = new AccessDetails(Optional.empty(), Optional.empty(), Optional.empty());

    /** Checks the three parts are given as present or empty. */
    public AccessDetails {
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(apartment, "apartment");
        Objects.requireNonNull(entranceCode, "entranceCode");
    }

    /**
     * Reads the three parts as typed or imported; a blank part is absent.
     *
     * @param floor the floor text
     * @param apartment the apartment text
     * @param entranceCode the entrance code text
     * @return the details
     */
    public static AccessDetails of(String floor, String apartment, String entranceCode) {
        return new AccessDetails(Floor.of(floor), Apartment.of(apartment), EntranceCode.of(entranceCode));
    }

    /**
     * Whether any part is given.
     *
     * @return {@code false} for {@link #NONE}
     */
    public boolean isGiven() {
        return floor.isPresent() || apartment.isPresent() || entranceCode.isPresent();
    }
}
