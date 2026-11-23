package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import java.time.Instant;
import java.util.Optional;

/**
 * What a test sees of one {@link Depot}, read through its getters: a value that compares by every
 * part, which the entity doesn't. A builder also lists the depots it wants as these.
 *
 * @param id the depot
 * @param name its name
 * @param location its coordinates, if it has any
 * @param hours its opening hours, if it has any
 * @param retiredAt when it was retired, or empty while it is active
 */
public record DepotDescription(
        DepotId id,
        DepotName name,
        Optional<GeoPoint> location,
        Optional<DepotHours> hours,
        Optional<Instant> retiredAt) {

    /**
     * Describes a depot.
     *
     * @param depot the depot
     * @return what its getters give
     */
    public static DepotDescription of(Depot depot) {
        return new DepotDescription(depot.id(), depot.name(), depot.location(), depot.hours(), depot.retiredAt());
    }
}
