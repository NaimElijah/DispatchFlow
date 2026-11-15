package com.dispatchflow.fleet.application.api;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.util.Objects;

/**
 * One vehicle of the fleet register, as the optimizer's inputs and Planning's checks need it
 * (FR-05, BR-40, BR-50): its display name, what it carries, where its routes start and end, and
 * whether it is active.
 *
 * <p>Built in M2.
 *
 * @param vehicle the vehicle
 * @param name its display name, which identifies it on the map, the timeline, the phone app and
 *     printouts (FR-198)
 * @param weightCapacity the most kilograms it carries
 * @param volumeCapacity the most litres it carries
 * @param startDepot where its routes start
 * @param endDepot where its routes end
 * @param active whether it is active, so it may be planned (FR-56, FR-193)
 */
public record VehicleFact(
        VehicleId vehicle,
        PlainText name,
        Weight weightCapacity,
        Volume volumeCapacity,
        DepotId startDepot,
        DepotId endDepot,
        boolean active) {

    /** Checks every part is present. */
    public VehicleFact {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(weightCapacity, "weightCapacity");
        Objects.requireNonNull(volumeCapacity, "volumeCapacity");
        Objects.requireNonNull(startDepot, "startDepot");
        Objects.requireNonNull(endDepot, "endDepot");
    }
}
