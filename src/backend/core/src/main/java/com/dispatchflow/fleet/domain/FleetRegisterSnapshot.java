package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;

/**
 * The fleet register's stored state: what {@link FleetRegister#snapshot()} returns and {@link
 * FleetRegister#restore} rebuilds from.
 *
 * @param version the version it was loaded at
 * @param depots every depot, active and retired, in the order they were added
 * @param vehicles every vehicle, active and retired, in the order they were added
 */
public record FleetRegisterSnapshot(Version version, List<DepotSnapshot> depots, List<VehicleSnapshot> vehicles) {

    /** Copies the lists. */
    public FleetRegisterSnapshot {
        Objects.requireNonNull(version, "version");
        depots = List.copyOf(depots);
        vehicles = List.copyOf(vehicles);
    }
}
