package com.dispatchflow.fleet.domain.register;

import java.util.List;

/**
 * What a test sees of a {@link FleetRegister}, read through its getters: every depot and vehicle,
 * in the register's order. Tests compare a register before and after a command, or with the
 * register a builder describes, through it.
 *
 * @param depots every depot, in the order they were added
 * @param vehicles every vehicle, in the order they were added
 */
public record RegisterDescription(List<DepotDescription> depots, List<VehicleDescription> vehicles) {

    /** Copies the lists. */
    public RegisterDescription {
        depots = List.copyOf(depots);
        vehicles = List.copyOf(vehicles);
    }

    /**
     * Describes a register.
     *
     * @param register the register
     * @return what its getters give
     */
    public static RegisterDescription of(FleetRegister register) {
        return new RegisterDescription(
                register.depots().stream().map(DepotDescription::of).toList(),
                register.vehicles().stream().map(VehicleDescription::of).toList());
    }
}
