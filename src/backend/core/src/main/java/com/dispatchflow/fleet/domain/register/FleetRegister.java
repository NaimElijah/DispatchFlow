package com.dispatchflow.fleet.domain.register;

import com.dispatchflow.fleet.domain.FleetError.DepotHasActiveVehicles;
import com.dispatchflow.fleet.domain.FleetError.DepotInUse;
import com.dispatchflow.fleet.domain.FleetError.DuplicatePlate;
import com.dispatchflow.fleet.domain.FleetError.DuplicateVehicleName;
import com.dispatchflow.fleet.domain.FleetError.FleetFull;
import com.dispatchflow.fleet.domain.FleetError.UnknownDepot;
import com.dispatchflow.fleet.domain.FleetError.UnknownVehicle;
import com.dispatchflow.fleet.domain.FleetError.VehicleInUse;
import com.dispatchflow.fleet.domain.FleetError.VehicleNotActive;
import com.dispatchflow.fleet.domain.PlanUses;
import com.dispatchflow.fleet.domain.Standing;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The company's depots and vehicles (DL §4.2): one register, with no ID, because two of its rules
 * are about the whole set (BR-36, BR-48) and the set is small by rule. One version check replaces a
 * set-wide check and a lock (DL §16).
 *
 * <p>Records are retired, never deleted (BR-43), and a retired vehicle counts toward neither the
 * vehicle limit nor uniqueness (FR-307, BR-48). Every method checks all its rules before it changes
 * anything, so a refused call leaves the register as it was. No method raises an event.
 */
public final class FleetRegister extends AggregateRoot {

    /** The most active vehicles the register holds (BR-36, NFR-04, ADR-060). */
    public static final int MAX_ACTIVE_VEHICLES = 15;

    // Insertion order, so a snapshot and a refusal's vehicle list come out the same every time.
    private final Map<DepotId, Depot> depots = new LinkedHashMap<>();
    private final Map<VehicleId, Vehicle> vehicles = new LinkedHashMap<>();

    private FleetRegister(@Nullable Version version) {
        super(version);
    }

    /**
     * Creates the company's register, before any depot or vehicle is added.
     *
     * @return an empty register, never saved
     */
    public static FleetRegister empty() {
        return new FleetRegister(null);
    }

    /**
     * Rebuilds the register from storage, checking the invariants that hold at rest: each depot and
     * vehicle appears once, every vehicle's depots are in the register, and among active vehicles
     * there are at most {@value #MAX_ACTIVE_VEHICLES}, each name and plate is used once, and every
     * depot they use is active, where names are compared as BR-48 compares them. Only persistence
     * adapters call it (DL §3.5).
     *
     * @param stored the stored state
     * @return the register at the stored version
     * @throws StoredStateInvalid when the stored state breaks one of those invariants
     */
    public static FleetRegister restore(FleetRegisterSnapshot stored) {
        FleetRegister register = new FleetRegister(stored.version());
        for (DepotSnapshot depot : stored.depots()) {
            if (register.depots.putIfAbsent(depot.id(), new Depot(depot)) != null) {
                throw invalid("a depot appears twice");
            }
        }
        for (VehicleSnapshot vehicle : stored.vehicles()) {
            if (register.vehicles.putIfAbsent(vehicle.id(), new Vehicle(vehicle)) != null) {
                throw invalid("a vehicle appears twice");
            }
        }
        register.checkAtRest();
        return register;
    }

    private void checkAtRest() {
        List<Vehicle> active = activeVehicleList();
        if (active.size() > MAX_ACTIVE_VEHICLES) {
            throw invalid("more active vehicles than the limit");
        }
        if (active.stream()
                        .map(vehicle -> nameKey(vehicle.details()))
                        .distinct()
                        .count()
                < active.size()) {
            throw invalid("two active vehicles share a name");
        }
        if (active.stream().map(vehicle -> vehicle.details().plate()).distinct().count() < active.size()) {
            throw invalid("two active vehicles share a plate");
        }
        for (Vehicle vehicle : vehicles.values()) {
            for (DepotId depot :
                    List.of(vehicle.details().startDepot(), vehicle.details().endDepot())) {
                Depot found = depots.get(depot);
                if (found == null) {
                    throw invalid("a vehicle uses a depot that is not in the register");
                }
                if (vehicle.isActive() && !found.isActive()) {
                    throw invalid("an active vehicle uses a retired depot");
                }
            }
        }
    }

    private static StoredStateInvalid invalid(String detail) {
        return new StoredStateInvalid(FleetRegister.class, detail);
    }

    /**
     * The register's state, for the repository to store and for Fleet's API to read.
     *
     * @return every depot and vehicle, in the order they were added
     */
    public FleetRegisterSnapshot snapshot() {
        return new FleetRegisterSnapshot(
                savedVersion(),
                depots.values().stream().map(Depot::snapshot).toList(),
                vehicles.values().stream().map(Vehicle::snapshot).toList());
    }

    /**
     * Adds a depot (FR-04). It may have no coordinates yet; such a depot starts and ends no route
     * (FR-197).
     *
     * @param id the new depot's ID, from {@code IdGenerator}
     * @param name its name
     * @param location its coordinates, if known (FR-130)
     * @param hours its opening hours, if it has any (FR-129)
     * @throws AssertionError when the ID is already in the register: the use case reused an ID, a
     *     programming error rather than a refusal
     */
    public void addDepot(DepotId id, DepotName name, Optional<GeoPoint> location, Optional<DepotHours> hours) {
        if (depots.containsKey(id)) {
            throw new AssertionError("a new depot needs a new ID");
        }
        depots.put(id, new Depot(new DepotSnapshot(id, name, location, hours, Optional.empty())));
    }

    /**
     * Renames an active depot and sets or clears its coordinates (FR-56, FR-130, FR-197).
     *
     * @param id the depot
     * @param name its new name
     * @param location its coordinates, or empty to clear them
     * @throws RuleViolation with {@link UnknownDepot} when no active depot has this ID
     */
    public void changeDepot(DepotId id, DepotName name, Optional<GeoPoint> location) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        activeDepot(id).change(name, location);
    }

    /**
     * Sets or clears an active depot's opening hours (FR-129, BR-46).
     *
     * @param id the depot
     * @param hours the new hours, or empty for a depot with no opening hours
     * @throws RuleViolation with {@link UnknownDepot} when no active depot has this ID
     */
    public void changeDepotHours(DepotId id, Optional<DepotHours> hours) {
        Objects.requireNonNull(hours, "hours");
        activeDepot(id).changeHours(hours);
    }

    /**
     * Retires a depot (BR-43). No active vehicle may start or end there (BR-40), and no uncancelled
     * plan for today or later may use it (FR-305).
     *
     * @param id the depot
     * @param uses the plans that use it, read through {@code RecordUsage}
     * @param today today's date in {@code Asia/Jerusalem}
     * @param now when it is retired
     * @throws RuleViolation with {@link UnknownDepot} when no active depot has this ID, {@link
     *     DepotHasActiveVehicles} when active vehicles use it, or {@link DepotInUse} naming the plan
     *     that uses it
     */
    public void retireDepot(DepotId id, PlanUses uses, LocalDate today, Instant now) {
        Objects.requireNonNull(uses, "uses");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(now, "now");
        Depot depot = activeDepot(id);
        List<VehicleId> using = activeVehicleList().stream()
                .filter(vehicle -> vehicle.uses(id))
                .map(Vehicle::id)
                .toList();
        if (!using.isEmpty()) {
            throw new RuleViolation(new DepotHasActiveVehicles(id, using));
        }
        uses.blockingOn(today).ifPresent(use -> {
            throw new RuleViolation(new DepotInUse(id, use.plan(), use.date()));
        });
        depot.retire(now);
    }

    /**
     * Registers a vehicle (FR-05, FR-131).
     *
     * @param id the new vehicle's ID, from {@code IdGenerator}
     * @param details its name, plate, capacity and depots
     * @throws RuleViolation with {@link FleetFull} when {@value #MAX_ACTIVE_VEHICLES} vehicles are
     *     already active (BR-36), {@link UnknownDepot} when either depot is not active (BR-40), or
     *     {@link DuplicateVehicleName} or {@link DuplicatePlate} when an active vehicle already has
     *     the name or the plate (BR-48)
     * @throws AssertionError when the ID is already in the register: the use case reused an ID, a
     *     programming error rather than a refusal
     */
    public void addVehicle(VehicleId id, VehicleDetails details) {
        if (vehicles.containsKey(id)) {
            throw new AssertionError("a new vehicle needs a new ID");
        }
        checkRoomForOneMore();
        checkDetails(details, id);
        vehicles.put(id, new Vehicle(new VehicleSnapshot(id, details, Optional.empty())));
    }

    /**
     * Changes an active vehicle's details (FR-56). A change applies to later plans (FR-306).
     *
     * @param id the vehicle
     * @param details its new name, plate, capacity and depots
     * @throws RuleViolation with {@link UnknownVehicle} when the register has no such vehicle,
     *     {@link VehicleNotActive} when it is retired, {@link UnknownDepot} when either depot is not
     *     active (BR-40), or {@link DuplicateVehicleName} or {@link DuplicatePlate} when another
     *     active vehicle has the name or the plate (BR-48)
     */
    public void changeVehicle(VehicleId id, VehicleDetails details) {
        Vehicle vehicle = activeVehicle(id);
        checkDetails(details, id);
        vehicle.change(details);
    }

    /**
     * Retires a vehicle (BR-43). It then counts toward neither the limit nor uniqueness (FR-307,
     * BR-48).
     *
     * @param id the vehicle
     * @param uses the plans that use it, read through {@code RecordUsage}
     * @param today today's date in {@code Asia/Jerusalem}
     * @param now when it is retired
     * @throws RuleViolation with {@link UnknownVehicle} when the register has no such vehicle,
     *     {@link VehicleNotActive} when it is already retired, or {@link VehicleInUse} naming the
     *     uncancelled plan for today or later that uses it (FR-305)
     */
    public void retireVehicle(VehicleId id, PlanUses uses, LocalDate today, Instant now) {
        Objects.requireNonNull(uses, "uses");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(now, "now");
        Vehicle vehicle = activeVehicle(id);
        uses.blockingOn(today).ifPresent(use -> {
            throw new RuleViolation(new VehicleInUse(id, use.plan(), use.date()));
        });
        vehicle.retire(now);
    }

    /**
     * Makes a retired vehicle active again. It counts again, so the limit, its depots and
     * uniqueness are checked as for a new one. An active vehicle stays as it is.
     *
     * @param id the vehicle
     * @throws RuleViolation with {@link UnknownVehicle} when the register has no such vehicle,
     *     {@link FleetFull} when {@value #MAX_ACTIVE_VEHICLES} vehicles are active (BR-36), {@link
     *     UnknownDepot} when one of its depots is retired (BR-40), or {@link DuplicateVehicleName} or
     *     {@link DuplicatePlate} when an active vehicle now has its name or plate (BR-48)
     */
    public void reactivateVehicle(VehicleId id) {
        Vehicle vehicle = vehicle(id);
        if (vehicle.isActive()) {
            return;
        }
        checkRoomForOneMore();
        checkDetails(vehicle.details(), id);
        vehicle.reactivate();
    }

    /**
     * Whether a vehicle may be rostered (DL §4.2): the answer {@code DayRoster} requires.
     *
     * @param id the vehicle
     * @return {@link Standing#ACTIVE} for an active vehicle; {@link Standing#NOT_ACTIVE} for a
     *     retired one or an ID the register doesn't hold
     */
    public Standing standingOf(VehicleId id) {
        Vehicle vehicle = vehicles.get(id);
        return vehicle != null && vehicle.isActive() ? Standing.ACTIVE : Standing.NOT_ACTIVE;
    }

    /**
     * The active vehicles, which are the ones new plans and rosters may use (FR-193).
     *
     * @return their IDs, in the order they were added
     */
    public List<VehicleId> activeVehicles() {
        return activeVehicleList().stream().map(Vehicle::id).toList();
    }

    private List<Vehicle> activeVehicleList() {
        return vehicles.values().stream().filter(Vehicle::isActive).toList();
    }

    private void checkRoomForOneMore() {
        if (activeVehicleList().size() >= MAX_ACTIVE_VEHICLES) {
            throw new RuleViolation(new FleetFull(MAX_ACTIVE_VEHICLES));
        }
    }

    /** BR-40 and BR-48 for the vehicle {@code self}, against every other active vehicle. */
    private void checkDetails(VehicleDetails details, VehicleId self) {
        activeDepot(details.startDepot());
        activeDepot(details.endDepot());
        activeOtherThan(self, details, FleetRegister::nameKey).ifPresent(other -> {
            throw new RuleViolation(new DuplicateVehicleName(other));
        });
        activeOtherThan(self, details, VehicleDetails::plate).ifPresent(other -> {
            throw new RuleViolation(new DuplicatePlate(other));
        });
    }

    /** BR-48 compares names by their matching form, so a lookalike is the same name. */
    private static VehicleNameKey nameKey(VehicleDetails details) {
        return VehicleNameKey.of(details.name());
    }

    private Optional<VehicleId> activeOtherThan(
            VehicleId self, VehicleDetails details, Function<VehicleDetails, Object> field) {
        Object wanted = field.apply(details);
        return activeVehicleList().stream()
                .filter(vehicle -> !vehicle.id().equals(self))
                .filter(vehicle -> field.apply(vehicle.details()).equals(wanted))
                .map(Vehicle::id)
                .findFirst();
    }

    private Depot activeDepot(DepotId id) {
        Depot depot = depots.get(id);
        if (depot == null || !depot.isActive()) {
            throw new RuleViolation(new UnknownDepot(id));
        }
        return depot;
    }

    private Vehicle vehicle(VehicleId id) {
        Vehicle vehicle = vehicles.get(id);
        if (vehicle == null) {
            throw new RuleViolation(new UnknownVehicle(id));
        }
        return vehicle;
    }

    private Vehicle activeVehicle(VehicleId id) {
        Vehicle vehicle = vehicle(id);
        if (!vehicle.isActive()) {
            throw new RuleViolation(new VehicleNotActive(id));
        }
        return vehicle;
    }
}
