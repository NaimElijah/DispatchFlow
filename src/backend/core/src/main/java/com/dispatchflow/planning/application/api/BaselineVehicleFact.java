package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.util.Objects;

/**
 * One vehicle of the fleet a plan's daily solve was given, as the baseline's solve needs it
 * (FR-39, FR-271, FR-342): what it carries, its depots and its driver's shift. Reporting can't
 * read Fleet, so it reads the fleet through Planning (placement P5).
 *
 * <p>Built in M5.
 *
 * @param vehicle the vehicle
 * @param weightCapacity the most kilograms it carries
 * @param volumeCapacity the most litres it carries
 * @param startDepot where its route starts
 * @param endDepot where its route ends
 * @param shift its driver's shift on the plan's date, whole (FR-271)
 */
public record BaselineVehicleFact(
        VehicleId vehicle,
        Weight weightCapacity,
        Volume volumeCapacity,
        DepotId startDepot,
        DepotId endDepot,
        TimeWindow shift) {

    /** Checks every part is present. */
    public BaselineVehicleFact {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(weightCapacity, "weightCapacity");
        Objects.requireNonNull(volumeCapacity, "volumeCapacity");
        Objects.requireNonNull(startDepot, "startDepot");
        Objects.requireNonNull(endDepot, "endDepot");
        Objects.requireNonNull(shift, "shift");
    }
}
