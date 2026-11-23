package com.dispatchflow.fleet.domain.roster;

import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.driver.DriverBuilder;
import com.dispatchflow.fleet.domain.register.FleetRegisterBuilder;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The test builder of {@link DayRoster}: {@code aRoster()} is a loaded, empty roster for Tuesday 15
 * June 2027. Every state is built as a snapshot and rebuilt through {@code restore}.
 */
final class DayRosterBuilder {

    static final DeliveryDate ORDINARY_DAY = DeliveryDate.of(LocalDate.of(2027, 6, 15));
    static final DeliveryDate SPRING_FORWARD = DeliveryDate.of(LocalDate.of(2027, 3, 26));
    static final DeliveryDate FALL_BACK = DeliveryDate.of(LocalDate.of(2027, 10, 31));
    static final AccountId DANA = DriverBuilder.DANA;
    static final AccountId OMER = AccountId.of(UUID.fromString("0190a5e2-0000-7000-b000-000000000002"));
    static final AccountId NOA = AccountId.of(UUID.fromString("0190a5e2-0000-7000-b000-000000000003"));
    static final VehicleId V1 = FleetRegisterBuilder.vehicleId(1);
    static final VehicleId V2 = FleetRegisterBuilder.vehicleId(2);
    static final VehicleId V3 = FleetRegisterBuilder.vehicleId(3);
    static final ShiftHours DAY_SHIFT = ShiftHours.of(LocalTime.of(8, 0), LocalTime.of(16, 0));
    static final Version LOADED = Version.of(7);
    static final UUID ROSTER_ID = UUID.fromString("0190a5e2-0000-7000-c000-000000000001");

    private DeliveryDate date = ORDINARY_DAY;
    private final List<RosterEntry> entries = new ArrayList<>();
    private final List<VehicleId> unavailableVehicles = new ArrayList<>();

    private DayRosterBuilder() {}

    static DayRosterBuilder aRoster() {
        return new DayRosterBuilder();
    }

    DayRosterBuilder on(DeliveryDate date) {
        this.date = date;
        return this;
    }

    /** A driver whose shift a dispatcher set, with the vehicle not set yet. */
    DayRosterBuilder withDriver(AccountId driver) {
        return withEntry(driver, new RosterVehicle.NotSet(), Availability.AVAILABLE);
    }

    /** A driver for whom a dispatcher chose no vehicle. */
    DayRosterBuilder withDriverWithoutVehicle(AccountId driver) {
        return withEntry(driver, new RosterVehicle.NoVehicle(), Availability.AVAILABLE);
    }

    DayRosterBuilder withDriverDriving(AccountId driver, VehicleId vehicle) {
        return withEntry(driver, new RosterVehicle.Assigned(vehicle), Availability.AVAILABLE);
    }

    DayRosterBuilder withUnavailableDriver(AccountId driver) {
        return withEntry(driver, new RosterVehicle.NotSet(), Availability.UNAVAILABLE);
    }

    DayRosterBuilder withEntry(AccountId driver, RosterVehicle vehicle, Availability availability) {
        return withEntry(new RosterEntry(driver, Shift.on(date, DAY_SHIFT), vehicle, availability));
    }

    DayRosterBuilder withEntry(RosterEntry entry) {
        entries.add(entry);
        return this;
    }

    DayRosterBuilder withUnavailableVehicle(VehicleId vehicle) {
        unavailableVehicles.add(vehicle);
        return this;
    }

    DayRosterSnapshot snapshot() {
        return new DayRosterSnapshot(date, LOADED, entries, unavailableVehicles);
    }

    DayRoster build() {
        return DayRoster.restore(snapshot());
    }
}
