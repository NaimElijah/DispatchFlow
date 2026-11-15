package com.dispatchflow.fleet.application.api;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;

/**
 * What changed in the fleet for one date since an instant, which Planning's {@code
 * ChangesSinceBasis} holds when a proposal is accepted (BR-25, BR-82, ADR-056).
 *
 * <p>Built in M2.
 *
 * @param changedVehicles the vehicles whose register entry or availability that date changed
 * @param changedDrivers the drivers whose roster entry that date changed
 */
public record FleetChanges(List<VehicleId> changedVehicles, List<AccountId> changedDrivers) {

    /** Copies the lists. */
    public FleetChanges {
        changedVehicles = List.copyOf(changedVehicles);
        changedDrivers = List.copyOf(changedDrivers);
    }
}
