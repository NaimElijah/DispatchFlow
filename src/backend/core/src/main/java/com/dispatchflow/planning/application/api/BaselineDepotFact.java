package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * One depot a baseline vehicle starts or ends at, as the plan's daily solve was given it (FR-39,
 * FR-342, BR-46). Its location is a kernel {@link GeoPoint}, so it prints redacted.
 *
 * <p>Built in M5.
 *
 * @param depot the depot
 * @param location where it is
 * @param hours its opening hours, or empty when it sets none
 */
public record BaselineDepotFact(DepotId depot, GeoPoint location, Optional<Hours> hours) implements PersonalData {

    /** Checks every part is present, possibly empty. */
    public BaselineDepotFact {
        Objects.requireNonNull(depot, "depot");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(hours, "hours");
    }

    /**
     * A form that hides the location.
     *
     * @return {@code BaselineDepotFact[redacted]}
     */
    @Override
    public String toString() {
        return "BaselineDepotFact[redacted]";
    }

    /**
     * A depot's opening hours, as Israel wall-clock times on every date (FR-129, ADR-103).
     *
     * <p>Built in M5.
     *
     * @param opens when routes may first leave
     * @param closes when routes must be back, after {@code opens}
     */
    public record Hours(LocalTime opens, LocalTime closes) {

        /** Checks both times are present. */
        public Hours {
            Objects.requireNonNull(opens, "opens");
            Objects.requireNonNull(closes, "closes");
        }
    }
}
