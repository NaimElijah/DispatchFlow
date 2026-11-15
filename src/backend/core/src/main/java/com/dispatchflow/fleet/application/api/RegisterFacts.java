package com.dispatchflow.fleet.application.api;

import java.util.List;

/**
 * The fleet register as other modules read it (DL §2.2): every depot and every vehicle,
 * which the optimizer's inputs are built from (TS §2.1, {@code SolveRequestAssembler}).
 *
 * <p>Built in M2.
 *
 * @param depots every depot, in the register's order
 * @param vehicles every vehicle, active or not, in the register's order
 */
public record RegisterFacts(List<DepotFact> depots, List<VehicleFact> vehicles) {

    /** Copies the lists. */
    public RegisterFacts {
        depots = List.copyOf(depots);
        vehicles = List.copyOf(vehicles);
    }
}
