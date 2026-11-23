package com.dispatchflow.fleet.domain.register;

import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.fleet.domain.PlanUses;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The test builder of {@link FleetRegister}: {@code aRegister()} is a saved register with two
 * active depots and no vehicles.
 *
 * <p>The builder lists the depots and vehicles it wants, in order; {@link #build} reaches that state
 * through the register's commands alone (ADR-127 decision 2), then saves it. It adds every depot,
 * then each vehicle in turn, retiring a retired one at once, and retires the retired depots last. A
 * state no command reaches, such as an active vehicle at a retired depot, fails the build. {@link
 * #description} is the state the builder describes, for a test to compare a register with.
 */
public final class FleetRegisterBuilder {

    static final DepotId NORTH = DepotId.of(UUID.fromString("0190a5e2-0000-7000-8000-000000000001"));
    static final DepotId SOUTH = DepotId.of(UUID.fromString("0190a5e2-0000-7000-8000-000000000002"));
    static final DepotId EAST = DepotId.of(UUID.fromString("0190a5e2-0000-7000-8000-000000000003"));
    static final Instant RETIRED_AT = Instant.parse("2027-01-10T08:00:00Z");
    static final GeoPoint NORTH_POINT = GeoPoint.of(32.79, 35.0);
    static final DepotHours DAY_HOURS = DepotHours.of(LocalTime.of(6, 0), LocalTime.of(20, 0));
    static final Capacity VAN_CAPACITY =
            Capacity.of(Weight.kilograms(new BigDecimal("800")), Volume.litres(new BigDecimal("6000")));

    private final List<DepotDescription> depots = new ArrayList<>();
    private final List<VehicleDescription> vehicles = new ArrayList<>();

    private FleetRegisterBuilder() {}

    static FleetRegisterBuilder aRegister() {
        return new FleetRegisterBuilder().withDepot(activeDepot(NORTH, "North")).withDepot(activeDepot(SOUTH, "South"));
    }

    static FleetRegisterBuilder aRegisterWithNoDepots() {
        return new FleetRegisterBuilder();
    }

    static DepotDescription activeDepot(DepotId id, String name) {
        return new DepotDescription(
                id, DepotName.of(name), Optional.of(NORTH_POINT), Optional.of(DAY_HOURS), Optional.empty());
    }

    public static VehicleId vehicleId(int n) {
        return VehicleId.of(new UUID(0x0190a5e2_0000_7000L, 0x9000_0000_0000_0000L + n));
    }

    static VehicleDetails details(int n) {
        return new VehicleDetails(VehicleName.of("Vehicle " + n), Plate.of("P" + n), VAN_CAPACITY, NORTH, NORTH);
    }

    static VehicleDescription activeVehicle(int n) {
        return new VehicleDescription(vehicleId(n), details(n), Optional.empty());
    }

    static VehicleDescription retiredVehicle(int n) {
        return new VehicleDescription(vehicleId(n), details(n), Optional.of(RETIRED_AT));
    }

    FleetRegisterBuilder withDepot(DepotDescription depot) {
        depots.add(depot);
        return this;
    }

    FleetRegisterBuilder withRetiredDepot(DepotId id, String name) {
        depots.add(new DepotDescription(
                id, DepotName.of(name), Optional.empty(), Optional.empty(), Optional.of(RETIRED_AT)));
        return this;
    }

    FleetRegisterBuilder withVehicle(VehicleDescription vehicle) {
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

    /**
     * The register this builder describes.
     *
     * @return the description {@link #build}'s register matches
     */
    RegisterDescription description() {
        return new RegisterDescription(depots, vehicles);
    }

    /**
     * Adds the depots and vehicles, retires the retired ones, and saves the register.
     *
     * @return the register, saved at version 0 with no pending event
     * @throws AssertionError when the commands reach another state than the builder describes
     */
    FleetRegister build() {
        FleetRegister register = FleetRegister.empty();
        depots.forEach(depot -> register.addDepot(depot.id(), depot.name(), depot.location(), depot.hours()));
        for (VehicleDescription vehicle : vehicles) {
            if (vehicle.retiredAt().isPresent()
                    && register.activeVehicles().size() == FleetRegister.MAX_ACTIVE_VEHICLES) {
                // A retired vehicle listed after a full fleet was added while one of the fleet stood
                // aside; that one comes back at once, so the state is the same.
                VehicleId asideForAMoment = register.activeVehicles().getFirst();
                retire(register, asideForAMoment, RETIRED_AT);
                addRetired(register, vehicle);
                register.reactivateVehicle(asideForAMoment);
            } else if (vehicle.retiredAt().isPresent()) {
                addRetired(register, vehicle);
            } else {
                register.addVehicle(vehicle.id(), vehicle.details());
            }
        }
        depots.stream()
                .filter(depot -> depot.retiredAt().isPresent())
                .forEach(depot -> register.retireDepot(
                        depot.id(),
                        PlanUses.none(),
                        dayOf(depot.retiredAt().get()),
                        depot.retiredAt().get()));
        if (!RegisterDescription.of(register).equals(description())) {
            throw new AssertionError(
                    "the commands reached " + RegisterDescription.of(register) + ", not " + description());
        }
        return asSaved(register);
    }

    private static void addRetired(FleetRegister register, VehicleDescription vehicle) {
        register.addVehicle(vehicle.id(), vehicle.details());
        retire(register, vehicle.id(), vehicle.retiredAt().orElseThrow());
    }

    private static void retire(FleetRegister register, VehicleId vehicle, Instant at) {
        register.retireVehicle(vehicle, PlanUses.none(), dayOf(at), at);
    }

    private static LocalDate dayOf(Instant at) {
        return at.atZone(IsraelDateTime.ZONE).toLocalDate();
    }
}
