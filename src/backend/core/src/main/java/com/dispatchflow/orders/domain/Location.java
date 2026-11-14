package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.GeoPoint;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a customer is (DL §3.3, §17): not known yet, geocoded with the match's quality, or placed by
 * hand. A hand-placed location always wins over geocoding (BR-13, FR-215). A customer's coordinates
 * are personal data (BR-98), and {@link GeoPoint} hides them when printed.
 */
public sealed interface Location {

    /**
     * The coordinates, where there are any.
     *
     * @return the point, or empty for {@link Missing}
     */
    Optional<GeoPoint> point();

    /** No coordinates: geocoding has not answered or found nothing, so the order drops as missing location (BR-14). */
    record Missing() implements Location {

        @Override
        public Optional<GeoPoint> point() {
            return Optional.empty();
        }
    }

    /**
     * Coordinates from the geocoder (FR-12).
     *
     * @param at the point found
     * @param quality how well the address matched (FR-63)
     */
    record Geocoded(GeoPoint at, MatchQuality quality) implements Location {

        /** Checks both parts are given. */
        public Geocoded {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(quality, "quality");
        }

        @Override
        public Optional<GeoPoint> point() {
            return Optional.of(at);
        }
    }

    /**
     * Coordinates a dispatcher placed on the map (FR-14, FR-218), used for every future order
     * (FR-215).
     *
     * @param at the point placed
     */
    record HandPlaced(GeoPoint at) implements Location {

        /** Checks the point is given. */
        public HandPlaced {
            Objects.requireNonNull(at, "at");
        }

        @Override
        public Optional<GeoPoint> point() {
            return Optional.of(at);
        }
    }
}
