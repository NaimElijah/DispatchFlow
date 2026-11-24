package com.dispatchflow.fleet.application.api;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * One depot of the fleet register, as the optimizer's inputs need it (FR-04, FR-129, BR-46): where
 * it is and when routes may leave and must be back. A depot with no location yet can't start or
 * end a route (FR-197). Its location is a kernel {@link GeoPoint}, so it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param depot the depot
 * @param location where it is, or empty until it is placed
 * @param hours its opening hours, or empty when it sets none
 */
public record DepotFact(DepotId depot, Optional<GeoPoint> location, Optional<OpeningHours> hours)
        implements PersonalData {

    /** Checks every part is present, possibly empty. */
    public DepotFact {
        Objects.requireNonNull(depot, "depot");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(hours, "hours");
    }

    /**
     * A form that hides the location.
     *
     * @return {@code DepotFact[redacted]}
     */
    @Override
    public String toString() {
        return "DepotFact[redacted]";
    }

    /**
     * A depot's opening hours, which apply on every delivery date as Israel wall-clock times
     * (FR-129, ADR-103).
     *
     * <p>To Be Implemented.
     *
     * @param opens when routes may first leave
     * @param closes when routes must be back, after {@code opens}
     */
    public record OpeningHours(LocalTime opens, LocalTime closes) {

        /** Checks both times are present. */
        public OpeningHours {
            Objects.requireNonNull(opens, "opens");
            Objects.requireNonNull(closes, "closes");
        }
    }
}
