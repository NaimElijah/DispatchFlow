package com.dispatchflow.fleet.domain;

import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The test builder of {@link FleetRegister}: {@code aRegister()} is a loaded register with two
 * active depots and no vehicles. Every state is built as a snapshot and rebuilt through {@code
 * restore}, so a test can start from any state at rest.
 */
final class FleetRegisterBuilder {

    static final DepotId NORTH = DepotId.of(UUID.fromString("0190a5e2-0000-7000-8000-000000000001"));
    static final DepotId SOUTH = DepotId.of(UUID.fromString("0190a5e2-0000-7000-8000-000000000002"));
    static final DepotId EAST = DepotId.of(UUID.fromString("0190a5e2-0000-7000-8000-000000000003"));
    static final Instant RETIRED_AT = Instant.parse("2027-01-10T08:00:00Z");
    static final Version LOADED = Version.of(3);
    static final GeoPoint NORTH_POINT = GeoPoint.of(32.79, 35.0);
    static final DepotHours DAY_HOURS = DepotHours.of(LocalTime.of(6, 0), LocalTime.of(20, 0));
    static final Capacity VAN_CAPACITY =
            Capacity.of(Weight.kilograms(new BigDecimal("800")), Volume.litres(new BigDecimal("6000")));

    private Version version = LOADED;
    private final List<DepotSnapshot> depots = new ArrayList<>();
    private final List<VehicleSnapshot> vehicles = new ArrayList<>();

    private FleetRegisterBuilder() {}

    static FleetRegisterBuilder aRegister() {
        return new FleetRegisterBuilder().withDepot(activeDepot(NORTH, "North")).withDepot(activeDepot(SOUTH, "South"));
    }

    static FleetRegisterBuilder aRegisterWithNoDepots() {
        return new FleetRegisterBuilder();
    }

    static DepotSnapshot activeDepot(DepotId id, String name) {
        return new DepotSnapshot(
                id, DepotName.of(name), Optional.of(NORTH_POINT), Optional.of(DAY_HOURS), Optional.empty());
    }

    static VehicleId vehicleId(int n) {
        return VehicleId.of(new UUID(0x0190a5e2_0000_7000L, 0x9000_0000_0000_0000L + n));
    }

    static VehicleDetails details(int n) {
        return new VehicleDetails(VehicleName.of("Vehicle " + n), Plate.of("P" + n), VAN_CAPACITY, NORTH, NORTH);
    }

    static VehicleSnapshot activeVehicle(int n) {
        return new VehicleSnapshot(vehicleId(n), details(n), Optional.empty());
    }

    static VehicleSnapshot retiredVehicle(int n) {
        return new VehicleSnapshot(vehicleId(n), details(n), Optional.of(RETIRED_AT));
    }

    FleetRegisterBuilder atVersion(Version version) {
        this.version = version;
        return this;
    }

    FleetRegisterBuilder withDepot(DepotSnapshot depot) {
        depots.add(depot);
        return this;
    }

    FleetRegisterBuilder withRetiredDepot(DepotId id, String name) {
        depots.add(
                new DepotSnapshot(id, DepotName.of(name), Optional.empty(), Optional.empty(), Optional.of(RETIRED_AT)));
        return this;
    }

    FleetRegisterBuilder withVehicle(VehicleSnapshot vehicle) {
        vehicles.add(vehicle);
        return this;
    }

    /** Active vehicles numbered from 1, each with its own name and plate, at the North depot. */
    FleetRegisterBuilder withActiveVehicles(int count) {
        for (int n = 1; n <= count; n++) {
            vehicles.add(activeVehicle(n));
        }
        return this;
    }

    FleetRegisterSnapshot snapshot() {
        return new FleetRegisterSnapshot(version, depots, vehicles);
    }

    FleetRegister build() {
        return FleetRegister.restore(snapshot());
    }
}
