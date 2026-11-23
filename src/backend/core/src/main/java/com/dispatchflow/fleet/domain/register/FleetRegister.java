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
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The company's depots and vehicles (DL §4.2): one register, with no ID, because two of its rules
 * are about the whole set (BR-36, BR-48) and the set is small by rule. One version check replaces a
 * set-wide check and a lock (DL §16).
 *
 * <p>Records are retired, never deleted (BR-43), and a retired vehicle counts toward neither the
 * vehicle limit nor uniqueness (FR-307, BR-48). Every method checks all its rules before it changes
 * anything, so a refused call leaves the register as it was, and every change ends by checking the
 * invariants at rest. No method raises an event.
 *
 * <p>It is mapped for JPA (ADR-126). Having no ID, it is stored under the constant {@link #KEY}
 * (ADR-127 decision 5). Its depots and vehicles are child entities, each list in the order they
 * were added, which the register searches by ID (ADR-127 decision 4).
 */
@Entity
@Table(schema = "fleet", name = "fleet_register")
public class FleetRegister extends AggregateRoot {

    /** The most active vehicles the register holds (BR-36, NFR-04, ADR-060). */
    public static final int MAX_ACTIVE_VEHICLES = 15;

    /**
     * The one stored register's key. The register has no ID, but every root keys on a UUID
     * (ADR-126 decision 10), so it keys on this constant, which the table allows as its only key.
     */
    public static final UUID KEY = UUID.fromString("10b6b5f9-ffc1-4b99-9eac-05d3d670a261");

    @Id
    private UUID id;

    // Insertion order, so the getters and a refusal's vehicle list come out the same every time.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fleet_register_id", nullable = false)
    @OrderColumn(name = "position", nullable = false)
    private List<Depot> depots = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fleet_register_id", nullable = false)
    @OrderColumn(name = "position", nullable = false)
    private List<Vehicle> vehicles = new ArrayList<>();

    /**
     * For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored};
     * and for {@link #empty}, which then sets the key.
     */
    protected FleetRegister() {}

    /**
     * Creates the company's register, before any depot or vehicle is added.
     *
     * @return an empty register, never saved
     */
    public static FleetRegister empty() {
        FleetRegister register = new FleetRegister();
        register.id = KEY;
        return register;
    }

    /**
     * Checks the register as Hibernate loaded it against the invariants that hold at rest (ADR-126
     * decision 8): each depot and vehicle appears once, every vehicle's depots are in the register,
     * and among active vehicles there are at most {@value #MAX_ACTIVE_VEHICLES}, each name and plate
     * is used once, and every depot they use is active, where names are compared as BR-48 compares
     * them.
     *
     * @throws StoredStateInvalid when a stored list has a gap in its positions, or the stored state
     *     breaks one of them
     */
    @PostLoad
    void checkStored() {
        brokenRule().ifPresent(broken -> {
            throw new StoredStateInvalid(FleetRegister.class, broken);
        });
    }

    /** The first invariant at rest the register breaks, if any: those {@link #checkStored} names. */
    private Optional<String> brokenRule() {
        if (StoredLists.anyHasGap(depots, vehicles)) {
            return Optional.of(StoredLists.GAP);
        }
        if (repeats(depots.stream().map(Depot::id).toList())) {
            return Optional.of("a depot appears twice");
        }
        if (repeats(vehicles.stream().map(Vehicle::id).toList())) {
            return Optional.of("a vehicle appears twice");
        }
        List<Vehicle> active = activeVehicleList();
        if (active.size() > MAX_ACTIVE_VEHICLES) {
            return Optional.of("more active vehicles than the limit");
        }
        if (repeats(active.stream().map(vehicle -> nameKey(vehicle.details())).toList())) {
            return Optional.of("two active vehicles share a name");
        }
        if (repeats(active.stream().map(vehicle -> vehicle.details().plate()).toList())) {
            return Optional.of("two active vehicles share a plate");
        }
        for (Vehicle vehicle : vehicles) {
            for (DepotId depot :
                    List.of(vehicle.details().startDepot(), vehicle.details().endDepot())) {
                Optional<Depot> found = findDepot(depot);
                if (found.isEmpty()) {
                    return Optional.of("a vehicle uses a depot that is not in the register");
                }
                if (vehicle.isActive() && !found.get().isActive()) {
                    return Optional.of("an active vehicle uses a retired depot");
                }
            }
        }
        return Optional.empty();
    }

    private static boolean repeats(List<?> values) {
        Set<Object> seen = new HashSet<>();
        return !values.stream().allMatch(seen::add);
    }

    /**
     * Checks the state a command left against every invariant at rest (ADR-126 decision 11). Every
     * rule a caller can break was checked before the command changed anything, so a broken
     * invariant here is a bug in the command, not a refusal, and the error rolls its transaction
     * back.
     */
    private void requireInvariants() {
        brokenRule().ifPresent(broken -> {
            throw new AssertionError("a fleet register command left a state that breaks: " + broken);
        });
    }

    /**
     * Every depot, active and retired.
     *
     * @return an unmodifiable copy of the list, in the order the depots were added; only this
     *     register's commands change the depots in it
     */
    public List<Depot> depots() {
        return List.copyOf(depots);
    }

    /**
     * Every vehicle, active and retired.
     *
     * @return an unmodifiable copy of the list, in the order the vehicles were added; only this
     *     register's commands change the vehicles in it
     */
    public List<Vehicle> vehicles() {
        return List.copyOf(vehicles);
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
        if (findDepot(id).isPresent()) {
            throw new AssertionError("a new depot needs a new ID");
        }
        depots.add(new Depot(id, name, location, hours));
        requireInvariants();
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
        requireInvariants();
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
        requireInvariants();
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
        requireInvariants();
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
        if (findVehicle(id).isPresent()) {
            throw new AssertionError("a new vehicle needs a new ID");
        }
        checkRoomForOneMore();
        checkDetails(details, id);
        vehicles.add(new Vehicle(id, details));
        requireInvariants();
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
        requireInvariants();
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
        requireInvariants();
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
        requireInvariants();
    }

    /**
     * Whether a vehicle may be rostered (DL §4.2): the answer {@code DayRoster} requires.
     *
     * @param id the vehicle
     * @return {@link Standing#ACTIVE} for an active vehicle; {@link Standing#NOT_ACTIVE} for a
     *     retired one or an ID the register doesn't hold
     */
    public Standing standingOf(VehicleId id) {
        return findVehicle(id).filter(Vehicle::isActive).isPresent() ? Standing.ACTIVE : Standing.NOT_ACTIVE;
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
        return vehicles.stream().filter(Vehicle::isActive).toList();
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

    private Optional<Depot> findDepot(DepotId id) {
        Objects.requireNonNull(id, "id");
        return depots.stream().filter(depot -> depot.id().equals(id)).findFirst();
    }

    private Optional<Vehicle> findVehicle(VehicleId id) {
        Objects.requireNonNull(id, "id");
        return vehicles.stream().filter(vehicle -> vehicle.id().equals(id)).findFirst();
    }

    private Depot activeDepot(DepotId id) {
        return findDepot(id).filter(Depot::isActive).orElseThrow(() -> new RuleViolation(new UnknownDepot(id)));
    }

    private Vehicle vehicle(VehicleId id) {
        return findVehicle(id).orElseThrow(() -> new RuleViolation(new UnknownVehicle(id)));
    }

    private Vehicle activeVehicle(VehicleId id) {
        Vehicle vehicle = vehicle(id);
        if (!vehicle.isActive()) {
            throw new RuleViolation(new VehicleNotActive(id));
        }
        return vehicle;
    }
}
