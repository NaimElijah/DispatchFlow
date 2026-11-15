package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A depot as the fleet register stores it (DL §4.2). The adapter builds its name, hours and point
 * as {@link FleetRegisterSnapshot} says, inside {@code StoredStateInvalid.whileRestoring}.
 *
 * @param id the depot
 * @param name its name
 * @param location its coordinates; a depot may have none yet, and then it starts and ends no route
 *     (FR-197)
 * @param hours its opening hours, if it has any (FR-129)
 * @param retiredAt when it was retired, or empty while it is active (BR-43)
 */
public record DepotSnapshot(
        DepotId id,
        DepotName name,
        Optional<GeoPoint> location,
        Optional<DepotHours> hours,
        Optional<Instant> retiredAt) {

    /** Checks every field is present. */
    public DepotSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(retiredAt, "retiredAt");
    }
}
