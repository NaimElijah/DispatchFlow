package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A vehicle as the fleet register stores it (DL §4.2). The adapter builds its details as {@link
 * FleetRegisterSnapshot} says, inside {@code StoredStateInvalid.whileRestoring}.
 *
 * @param id the vehicle
 * @param details its name, plate, capacity and depots
 * @param retiredAt when it was retired, or empty while it is active (BR-43)
 */
public record VehicleSnapshot(VehicleId id, VehicleDetails details, Optional<Instant> retiredAt) {

    /** Checks every field is present. */
    public VehicleSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(retiredAt, "retiredAt");
    }
}
