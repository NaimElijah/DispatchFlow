package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.shared.Version;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The fleet register's stored state: what {@link FleetRegister#snapshot()} returns and {@link
 * FleetRegister#restore} rebuilds from.
 *
 * <p>The persistence adapter builds every stored value inside {@link
 * com.dispatchflow.shared.StoredStateInvalid#whileRestoring}, so a value that the rules now refuse
 * is an incident, never a user-facing refusal. Here that covers every plate, weight, volume, depot
 * hours and point, and the names, which {@link VehicleName#ofStored} and {@link DepotName#ofStored}
 * rebuild (ADR-106). A value has an {@code ofStored} factory where the stored rule differs from the
 * input rule, as {@code PhoneNumber.ofStored} accepts any E.164 text, or where it already wraps the
 * input rule this way, as the names' factories do.
 *
 * @param version the version it was loaded at, or null for an aggregate never saved
 *     (ADR-126)
 * @param depots every depot, active and retired, in the order they were added
 * @param vehicles every vehicle, active and retired, in the order they were added
 */
public record FleetRegisterSnapshot(
        @Nullable Version version, List<DepotSnapshot> depots, List<VehicleSnapshot> vehicles) {

    /** Copies the lists. */
    public FleetRegisterSnapshot {
        depots = List.copyOf(depots);
        vehicles = List.copyOf(vehicles);
    }
}
