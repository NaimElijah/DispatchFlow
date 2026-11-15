package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.MatchQuality;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * What the geocoding provider found for an address: the point and how well the address matched
 * (FR-12, FR-63, ADR-085). The distance to the nearest road is not the provider's to answer:
 * {@link RoadSnap} asks the road network for it. The point is a customer's location, so it prints
 * redacted.
 *
 * <p>Built in M7.
 *
 * @param at the point found
 * @param quality how well the address matched; a partial or ambiguous match is flagged (FR-63)
 */
public record GeocodeMatch(GeoPoint at, MatchQuality quality) implements PersonalData {

    /** Checks both parts are present. */
    public GeocodeMatch {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(quality, "quality");
    }

    /**
     * A form that hides the point.
     *
     * @return {@code GeocodeMatch[redacted]}
     */
    @Override
    public String toString() {
        return "GeocodeMatch[redacted]";
    }
}
