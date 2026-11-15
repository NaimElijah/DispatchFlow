package com.dispatchflow.fleet.domain.driver;

import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.register.FleetRegisterBuilder;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

/**
 * The test builder of {@link Driver}: {@code aDriver()} is a loaded, active driver with a default
 * shift of 08:00 to 16:00, a default vehicle and no weekly shifts. Every state is built as a
 * snapshot and rebuilt through {@code restore}.
 */
public final class DriverBuilder {

    public static final AccountId DANA = AccountId.of(UUID.fromString("0190a5e2-0000-7000-b000-000000000001"));
    public static final DriverContact DANA_CONTACT =
            new DriverContact(DriverName.of("Dana Levi"), PhoneNumber.parse("052-345-6789"));
    static final ShiftHours DEFAULT_SHIFT = ShiftHours.of(LocalTime.of(8, 0), LocalTime.of(16, 0));
    public static final VehicleId DEFAULT_VEHICLE = FleetRegisterBuilder.vehicleId(1);
    static final Version LOADED = Version.of(5);
    static final Instant RETIRED_AT = Instant.parse("2026-06-15T09:00:00Z");
    static final Instant ANONYMIZED_AT = Instant.parse("2027-06-15T09:00:00Z");

    private AccountId id = DANA;
    private DriverStatus status = new DriverStatus.Active();
    private Optional<DriverContact> contact = Optional.of(DANA_CONTACT);
    private ShiftHours defaultShift = DEFAULT_SHIFT;
    private VehicleId defaultVehicle = DEFAULT_VEHICLE;
    private WeeklyShifts weeklyShifts = WeeklyShifts.none();

    private DriverBuilder() {}

    public static DriverBuilder aDriver() {
        return new DriverBuilder();
    }

    DriverBuilder withId(AccountId id) {
        this.id = id;
        return this;
    }

    DriverBuilder retired() {
        this.status = new DriverStatus.Retired(RETIRED_AT);
        return this;
    }

    DriverBuilder anonymized() {
        this.status = new DriverStatus.Anonymized(Optional.of(RETIRED_AT), ANONYMIZED_AT);
        this.contact = Optional.empty();
        return this;
    }

    DriverBuilder withStatus(DriverStatus status) {
        this.status = status;
        return this;
    }

    DriverBuilder withContact(Optional<DriverContact> contact) {
        this.contact = contact;
        return this;
    }

    DriverBuilder withDefaults(ShiftHours shift, VehicleId vehicle) {
        this.defaultShift = shift;
        this.defaultVehicle = vehicle;
        return this;
    }

    public DriverBuilder withWeeklyShifts(WeeklyShifts weeklyShifts) {
        this.weeklyShifts = weeklyShifts;
        return this;
    }

    public DriverSnapshot snapshot() {
        return new DriverSnapshot(id, LOADED, status, contact, defaultShift, defaultVehicle, weeklyShifts);
    }

    public Driver build() {
        return Driver.restore(snapshot());
    }
}
