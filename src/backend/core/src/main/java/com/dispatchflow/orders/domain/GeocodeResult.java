package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * The geocoder's answer for one address, which the geocoding adapter translates into this
 * context's language (FR-12, FR-63). It names the normalized address it was looked up for, so an
 * answer that arrives after the address changed is ignored (BR-104). An unreachable geocoder is
 * retried by the outbox for 24 hours (FR-64); only when the retries stop does it become a result,
 * {@link GaveUp}.
 */
public sealed interface GeocodeResult extends PersonalData {

    /**
     * The address the lookup was for.
     *
     * @return the normalized address
     */
    NormalizedAddress lookedUp();

    /**
     * The geocoder found the address.
     *
     * @param lookedUp the address the lookup was for
     * @param at the point found
     * @param quality how well the address matched
     * @param metresToRoad the distance from the point to the nearest road, as the road network
     *     measures it (FR-63)
     */
    record Found(NormalizedAddress lookedUp, GeoPoint at, MatchQuality quality, double metresToRoad)
            implements GeocodeResult {

        /** Checks the parts are given. */
        public Found {
            Objects.requireNonNull(lookedUp, "lookedUp");
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(quality, "quality");
        }

        /**
         * A form that hides the answer, because it holds personal data.
         *
         * @return {@code GeocodeResult.Found[redacted]}
         */
        @Override
        public String toString() {
            return "GeocodeResult.Found[redacted]";
        }
    }

    /**
     * The geocoder found no match (FR-63, FR-218).
     *
     * @param lookedUp the address the lookup was for
     */
    record NotFound(NormalizedAddress lookedUp) implements GeocodeResult {

        /** Checks the address is given. */
        public NotFound {
            Objects.requireNonNull(lookedUp, "lookedUp");
        }

        /**
         * A form that hides the answer, because it holds personal data.
         *
         * @return {@code GeocodeResult.NotFound[redacted]}
         */
        @Override
        public String toString() {
            return "GeocodeResult.NotFound[redacted]";
        }
    }

    /**
     * The geocoder could not be reached for 24 hours, so the outbox stopped retrying (FR-64, DL §12).
     * The customer stays flagged for a hand-placed location.
     *
     * @param lookedUp the address the lookup was for
     */
    record GaveUp(NormalizedAddress lookedUp) implements GeocodeResult {

        /** Checks the address is given. */
        public GaveUp {
            Objects.requireNonNull(lookedUp, "lookedUp");
        }

        /**
         * A form that hides the answer, because it holds personal data.
         *
         * @return {@code GeocodeResult.GaveUp[redacted]}
         */
        @Override
        public String toString() {
            return "GeocodeResult.GaveUp[redacted]";
        }
    }
}
