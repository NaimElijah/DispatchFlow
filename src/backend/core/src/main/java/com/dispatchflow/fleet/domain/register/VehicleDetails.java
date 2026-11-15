package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.DepotId;
import java.util.Objects;

/**
 * What a dispatcher registers or changes about a vehicle (FR-05, FR-131): its display name, plate,
 * capacity, and the depots its routes start and end at (BR-40).
 *
 * @param name the display name
 * @param plate the normalized plate
 * @param capacity its weight and volume limits
 * @param startDepot where its routes start
 * @param endDepot where its routes end
 */
public record VehicleDetails(VehicleName name, Plate plate, Capacity capacity, DepotId startDepot, DepotId endDepot) {

    /** Checks every field is present. */
    public VehicleDetails {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(plate, "plate");
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(startDepot, "startDepot");
        Objects.requireNonNull(endDepot, "endDepot");
    }
}
