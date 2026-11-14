package com.dispatchflow.fleet.domain;

import com.dispatchflow.fleet.domain.FleetError.DriverAlreadyHasVehicle;
import com.dispatchflow.fleet.domain.FleetError.DriverMarkedUnavailable;
import com.dispatchflow.fleet.domain.FleetError.DriverNotRostered;
import com.dispatchflow.fleet.domain.FleetError.NotAnActiveDriver;
import com.dispatchflow.fleet.domain.FleetError.VehicleAlreadyAssigned;
import com.dispatchflow.fleet.domain.FleetError.VehicleNotActive;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * One delivery date's roster (DL §4.2, §17): who works, their shift, the vehicle each drives, and
 * the vehicles marked unavailable (FR-54, FR-07, FR-82). On its date a driver drives at most one
 * vehicle and a vehicle has at most one driver, and every shift lies on the date (FR-190).
 *
 * <p>Dispatchers change it, and so does Planning's hand-over listener through {@link
 * #moveVehicleWithRoute} (FR-191). Every method checks all its rules before it changes anything,
 * and no method raises an event.
 */
public final class DayRoster extends AggregateRoot {

    private final DeliveryDate date;
    // Insertion order, so a snapshot lists drivers and vehicles the same way every time.
    private final Map<AccountId, RosterEntry> entries = new LinkedHashMap<>();
    private final Set<VehicleId> unavailableVehicles = new LinkedHashSet<>();

    private DayRoster(DeliveryDate date, Version version) {
        super(version);
        this.date = Objects.requireNonNull(date, "date");
    }

    /**
     * Opens an empty roster for a date. {@link #fill} then gives it the drivers' defaults.
     *
     * @param date the delivery date
     * @return the new roster at {@link Version#NEW}
     */
    public static DayRoster open(DeliveryDate date) {
        return new DayRoster(date, Version.NEW);
    }

    /**
     * Rebuilds a roster from storage, checking the invariants that hold at rest: each driver and
     * each unavailable vehicle appears once, no vehicle has two drivers, and every shift lies on the
     * roster's date (FR-190). Only persistence adapters call it (DL §3.5).
     *
     * @param stored the stored state
     * @return the roster at the stored version
     * @throws StoredStateInvalid when the stored state breaks one of those invariants
     */
    public static DayRoster restore(DayRosterSnapshot stored) {
        DayRoster roster = new DayRoster(stored.date(), stored.version());
        Set<VehicleId> driven = new LinkedHashSet<>();
        for (RosterEntry entry : stored.entries()) {
            if (roster.entries.putIfAbsent(entry.driver(), entry) != null) {
                throw invalid("a driver appears twice");
            }
            if (entry.drivenVehicle().filter(vehicle -> !driven.add(vehicle)).isPresent()) {
                throw invalid("a vehicle has two drivers");
            }
            if (!entry.shift().date().equals(stored.date())) {
                throw invalid("a shift lies on another date");
            }
        }
        for (VehicleId vehicle : stored.unavailableVehicles()) {
            if (!roster.unavailableVehicles.add(vehicle)) {
                throw invalid("a vehicle is marked unavailable twice");
            }
        }
        return roster;
    }

    private static StoredStateInvalid invalid(String detail) {
        return new StoredStateInvalid(DayRoster.class, detail);
    }

    /**
     * The roster's state, for the repository to store and for Fleet's application layer to map into
     * its API's own types.
     *
     * @return the stored form
     */
    public DayRosterSnapshot snapshot() {
        return new DayRosterSnapshot(date, version(), List.copyOf(entries.values()), List.copyOf(unavailableVehicles));
    }

    /**
     * The roster's key.
     *
     * @return the delivery date it is for
     */
    public DeliveryDate date() {
        return date;
    }

    /**
     * Fills the values of this date that nobody has set from each driver's default (FR-79, FR-189,
     * ADR-103). A driver not yet on the roster gets their default shift and vehicle. A driver
     * already on it keeps their shift and availability, and gets their default vehicle only if
     * their vehicle is not set, on a day off too; a vehicle or "no vehicle" a dispatcher chose
     * stays. A default with no hours, for a weekday the driver's weekly shifts leave off, adds no
     * entry and is not reported. Filling again changes nothing, and a later default for a driver
     * already in the list is ignored.
     *
     * <p>A driver whose default can't be used is skipped and reported, and every other driver is
     * still filled: the driver is not active, the default vehicle is retired or unknown or another
     * driver has it, or the shift falls in the hour the clock skips on this date. A skipped driver's
     * entry stays as it was.
     *
     * @param defaults each active driver record's default, from {@link Driver#rosterDefaultOn},
     *     with Access's answer on the account
     * @param activeVehicles the fleet register's active vehicles, from {@link
     *     FleetRegister#activeVehicles()}
     * @return the drivers filled and the drivers skipped, each with its reason
     */
    public RosterFill fill(List<RosterDefault> defaults, List<VehicleId> activeVehicles) {
        Set<VehicleId> active = Set.copyOf(activeVehicles);
        Set<AccountId> seen = new LinkedHashSet<>();
        List<AccountId> filled = new ArrayList<>();
        List<SkippedDriver> skipped = new ArrayList<>();
        for (RosterDefault fallback : defaults) {
            AccountId driver = fallback.driver();
            RosterEntry current = entries.get(driver);
            boolean nothingToFill = current != null && !(current.vehicle() instanceof RosterVehicle.NotSet);
            // On a day off a driver not yet on the roster isn't rostered, so there is nothing to report.
            boolean dayOffAndNotRostered = current == null && fallback.hours().isEmpty();
            if (!seen.add(driver) || nothingToFill || dayOffAndNotRostered) {
                continue;
            }
            Optional<DomainError> refusal = refusalOf(fallback, current, active);
            if (refusal.isPresent()) {
                skipped.add(new SkippedDriver(driver, refusal.get()));
                continue;
            }
            RosterVehicle vehicle = new RosterVehicle.Assigned(fallback.vehicle());
            entries.put(
                    driver,
                    current == null
                            ? new RosterEntry(
                                    driver,
                                    Shift.on(date, fallback.hours().orElseThrow()),
                                    vehicle,
                                    Availability.AVAILABLE)
                            : current.withVehicle(vehicle));
            filled.add(driver);
        }
        return new RosterFill(filled, skipped);
    }

    /** Why a default can't fill its driver's unset values, or empty when it can. */
    private Optional<DomainError> refusalOf(
            RosterDefault fallback, @Nullable RosterEntry current, Set<VehicleId> activeVehicles) {
        if (fallback.standing() != Standing.ACTIVE) {
            return Optional.of(new NotAnActiveDriver(fallback.driver()));
        }
        if (current == null) {
            try {
                Shift.on(date, fallback.hours().orElseThrow());
            } catch (RuleViolation skippedHour) {
                // The shift's refusal is the reason this driver is skipped, so it is reported, not thrown.
                return Optional.of(skippedHour.error());
            }
        }
        if (!activeVehicles.contains(fallback.vehicle())) {
            return Optional.of(new VehicleNotActive(fallback.vehicle()));
        }
        return holderOf(fallback.vehicle()).map(holder -> new VehicleAlreadyAssigned(fallback.vehicle(), holder));
    }

    /**
     * Sets a driver's shift on this date (FR-54). A driver not yet on the roster is added,
     * available, with their vehicle not set, so a later fill may give them their default vehicle
     * (FR-79). A driver already on it keeps their vehicle and availability.
     *
     * @param driver the driver
     * @param hours the shift's wall-clock hours, placed on this date
     * @param standing whether the driver is active and their account holds the Driver role
     * @throws RuleViolation with {@link NotAnActiveDriver} when the driver is not active, or {@link
     *     NonexistentLocalTime} when the shift starts or ends in the hour the clock skips on this
     *     date (BR-111)
     */
    public void changeShift(AccountId driver, ShiftHours hours, Standing standing) {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(standing, "standing");
        requireActiveDriver(driver, standing);
        Shift shift = Shift.on(date, hours);
        RosterEntry current = entries.get(driver);
        entries.put(
                driver,
                current == null
                        ? new RosterEntry(driver, shift, new RosterVehicle.NotSet(), Availability.AVAILABLE)
                        : current.withShift(shift));
    }

    /**
     * Gives a rostered driver a vehicle for this date (FR-54). Giving them the vehicle they already
     * drive changes nothing.
     *
     * @param driver the driver
     * @param vehicle the vehicle
     * @param driverStanding whether the driver is active and their account holds the Driver role
     * @param vehicleStanding whether the vehicle is active, from {@link FleetRegister#standingOf}
     * @throws RuleViolation with {@link NotAnActiveDriver}, {@link VehicleNotActive}, {@link
     *     DriverNotRostered} when the driver has no shift this date, {@link VehicleAlreadyAssigned}
     *     when another driver drives the vehicle, or {@link DriverAlreadyHasVehicle} when the driver
     *     drives another vehicle
     */
    public void assign(AccountId driver, VehicleId vehicle, Standing driverStanding, Standing vehicleStanding) {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driverStanding, "driverStanding");
        Objects.requireNonNull(vehicleStanding, "vehicleStanding");
        requireActiveDriver(driver, driverStanding);
        requireActiveVehicle(vehicle, vehicleStanding);
        RosterEntry entry = rostered(driver);
        if (entry.drives(vehicle)) {
            return;
        }
        Optional<AccountId> holder = holderOf(vehicle);
        if (holder.isPresent()) {
            throw new RuleViolation(new VehicleAlreadyAssigned(vehicle, holder.get()));
        }
        Optional<VehicleId> own = entry.drivenVehicle();
        if (own.isPresent()) {
            throw new RuleViolation(new DriverAlreadyHasVehicle(driver, own.get()));
        }
        entries.put(driver, entry.withVehicle(new RosterVehicle.Assigned(vehicle)));
    }

    /**
     * Records that a rostered driver drives no vehicle on this date; they still work their shift.
     * The choice counts as set, so a fill leaves it alone (FR-79).
     *
     * @param driver the driver
     * @throws RuleViolation with {@link DriverNotRostered} when the driver has no shift this date
     */
    public void unassign(AccountId driver) {
        entries.put(driver, rostered(driver).withVehicle(new RosterVehicle.NoVehicle()));
    }

    /**
     * Marks a rostered driver unavailable for the rest of this date (FR-82). Only a person then
     * starts a re-optimization (FR-332).
     *
     * @param driver the driver
     * @throws RuleViolation with {@link DriverNotRostered} when the driver has no shift this date
     */
    public void markDriverUnavailable(AccountId driver) {
        entries.put(driver, rostered(driver).withAvailability(Availability.UNAVAILABLE));
    }

    /**
     * Marks a rostered driver available again, effective from the next solve or re-optimization
     * (FR-300).
     *
     * @param driver the driver
     * @throws RuleViolation with {@link DriverNotRostered} when the driver has no shift this date
     */
    public void markDriverAvailable(AccountId driver) {
        entries.put(driver, rostered(driver).withAvailability(Availability.AVAILABLE));
    }

    /**
     * Marks a vehicle unavailable on this date, whether or not anyone drives it: during the day
     * (FR-07), or before it starts (FR-301). It keeps its driver, and BR-20 refuses publishing it.
     *
     * @param vehicle the vehicle
     * @param standing whether the vehicle is active, from {@link FleetRegister#standingOf}
     * @throws RuleViolation with {@link VehicleNotActive} when the vehicle is retired or the
     *     register doesn't hold it
     */
    public void markVehicleUnavailable(VehicleId vehicle, Standing standing) {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(standing, "standing");
        requireActiveVehicle(vehicle, standing);
        unavailableVehicles.add(vehicle);
    }

    /**
     * Marks a vehicle available again, effective from the next solve or re-optimization (FR-300).
     *
     * @param vehicle the vehicle
     */
    public void markVehicleAvailable(VehicleId vehicle) {
        unavailableVehicles.remove(vehicle);
    }

    /**
     * Whether a vehicle is marked unavailable on this date.
     *
     * @param vehicle the vehicle
     * @return {@code true} once marked, until marked available again
     */
    public boolean isUnavailable(VehicleId vehicle) {
        return unavailableVehicles.contains(vehicle);
    }

    /**
     * Moves a vehicle to the driver a route was handed to, so the roster follows the plan (FR-191).
     * Planning's listener on {@code RouteHandedOver} calls it after the hand-over was checked with
     * {@link #checkAssignable}; so the new driver's standing and availability are not checked
     * again, and a repeated delivery that finds the vehicle already moved changes nothing. The
     * driver it leaves has no vehicle, as a set value, so a fill doesn't give them their default.
     *
     * @param vehicle the handed-over route's vehicle
     * @param from the driver the route was taken from
     * @param to the driver the route was handed to
     * @throws RuleViolation with {@link VehicleAlreadyAssigned} when a driver other than {@code
     *     from} now drives the vehicle, {@link DriverNotRostered} when {@code to} has no shift this
     *     date, or {@link DriverAlreadyHasVehicle} when {@code to} drives another vehicle
     */
    public void moveVehicleWithRoute(VehicleId vehicle, AccountId from, AccountId to) {
        Optional<AccountId> holder = holderOf(vehicle);
        if (holder.filter(to::equals).isPresent()) {
            return;
        }
        if (holder.isPresent() && !holder.get().equals(from)) {
            throw new RuleViolation(new VehicleAlreadyAssigned(vehicle, holder.get()));
        }
        RosterEntry receiver = rostered(to);
        Optional<VehicleId> own = receiver.drivenVehicle();
        if (own.isPresent()) {
            throw new RuleViolation(new DriverAlreadyHasVehicle(to, own.get()));
        }
        holder.ifPresent(giver -> entries.put(giver, entries.get(giver).withVehicle(new RosterVehicle.NoVehicle())));
        entries.put(to, receiver.withVehicle(new RosterVehicle.Assigned(vehicle)));
    }

    /**
     * Whether a driver can take a vehicle on this date, as a hand-over needs (FR-302): they are
     * active, on the roster, available, and drive no other vehicle. It changes nothing.
     *
     * @param vehicle the vehicle the route is driven with
     * @param driver the driver it would go to
     * @param standing Access's answer on the account combined with the driver record's state
     *     (ADR-062)
     * @return {@link Assignability.Assignable} with the driver's shift, or {@link
     *     Assignability.NotAssignable} with {@link NotAnActiveDriver}, {@link DriverNotRostered},
     *     {@link DriverMarkedUnavailable} or {@link DriverAlreadyHasVehicle}
     */
    public Assignability checkAssignable(VehicleId vehicle, AccountId driver, Standing standing) {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(standing, "standing");
        if (standing != Standing.ACTIVE) {
            return new Assignability.NotAssignable(new NotAnActiveDriver(driver));
        }
        RosterEntry entry = entries.get(driver);
        if (entry == null) {
            return new Assignability.NotAssignable(new DriverNotRostered(driver));
        }
        if (entry.availability() == Availability.UNAVAILABLE) {
            return new Assignability.NotAssignable(new DriverMarkedUnavailable(driver));
        }
        Optional<VehicleId> other = entry.drivenVehicle().filter(own -> !own.equals(vehicle));
        if (other.isPresent()) {
            return new Assignability.NotAssignable(new DriverAlreadyHasVehicle(driver, other.get()));
        }
        return new Assignability.Assignable(entry.shift());
    }

    /**
     * A driver's entry on this date.
     *
     * @param driver the driver
     * @return their entry, or empty when they are not on the roster
     */
    public Optional<RosterEntry> entryOf(AccountId driver) {
        return Optional.ofNullable(entries.get(driver));
    }

    private Optional<AccountId> holderOf(VehicleId vehicle) {
        return entries.values().stream()
                .filter(entry -> entry.drives(vehicle))
                .map(RosterEntry::driver)
                .findFirst();
    }

    private RosterEntry rostered(AccountId driver) {
        RosterEntry entry = entries.get(Objects.requireNonNull(driver, "driver"));
        if (entry == null) {
            throw new RuleViolation(new DriverNotRostered(driver));
        }
        return entry;
    }

    private static void requireActiveDriver(AccountId driver, Standing standing) {
        if (standing != Standing.ACTIVE) {
            throw new RuleViolation(new NotAnActiveDriver(driver));
        }
    }

    private static void requireActiveVehicle(VehicleId vehicle, Standing standing) {
        if (standing != Standing.ACTIVE) {
            throw new RuleViolation(new VehicleNotActive(vehicle));
        }
    }
}
