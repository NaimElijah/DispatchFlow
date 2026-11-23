package com.dispatchflow.fleet.domain.driver;

import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.fleet.domain.PlanUses;
import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.Standing;
import com.dispatchflow.fleet.domain.register.FleetRegisterBuilder;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

/**
 * The test builder of {@link Driver}: {@code aDriver()} is a saved, active driver with a default
 * shift of 08:00 to 16:00, a default vehicle and no weekly shifts.
 *
 * <p>The builder describes the state it wants; {@link #build} reaches it through the factory and
 * the commands alone (ADR-127 decision 2), then saves it, so its events are gone. A state no command
 * reaches, such as an active driver without a name, fails the build. {@link #description} is the
 * state the builder describes, for a test to compare a driver with.
 */
public final class DriverBuilder {

    public static final AccountId DANA = AccountId.of(UUID.fromString("0190a5e2-0000-7000-b000-000000000001"));
    public static final DriverContact DANA_CONTACT =
            new DriverContact(DriverName.of("Dana Levi"), PhoneNumber.parse("052-345-6789"));
    static final ShiftHours DEFAULT_SHIFT = ShiftHours.of(LocalTime.of(8, 0), LocalTime.of(16, 0));
    public static final VehicleId DEFAULT_VEHICLE = FleetRegisterBuilder.vehicleId(1);
    /** The version {@link #build} leaves: a driver's first save stores 0 (ADR-126). */
    static final Version LOADED = Version.of(0);

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

    /**
     * The driver this builder describes.
     *
     * @return the description {@link #build}'s driver matches
     */
    public DriverDescription description() {
        return new DriverDescription(id, status, contact, defaultShift, defaultVehicle, weeklyShifts);
    }

    /**
     * Registers the driver, sets their weekly shifts while they are active, then retires and
     * anonymizes them as the status says, and saves them.
     *
     * @return the driver, at {@link #LOADED} with no pending event
     * @throws AssertionError when the commands reach another state than the builder describes
     */
    public Driver build() {
        if ((status instanceof DriverStatus.Anonymized) == contact.isPresent()) {
            throw new AssertionError("no command leaves a driver's contact at odds with their status");
        }
        Driver driver =
                Driver.register(id, contact.orElse(DANA_CONTACT), defaultShift, defaultVehicle, Standing.ACTIVE);
        if (!weeklyShifts.isEmpty()) {
            driver.changeWeeklyShifts(weeklyShifts);
        }
        switch (status) {
            case DriverStatus.Active active -> {}
            case DriverStatus.Retired(Instant at) -> retire(driver, at);
            case DriverStatus.Anonymized(Optional<Instant> retiredAt, Instant at) -> {
                if (retiredAt.isEmpty()) {
                    driver.anonymizeWithAccount(at);
                } else {
                    retire(driver, retiredAt.get());
                    boolean retentionOver = !at.isBefore(retiredAt
                            .get()
                            .atZone(IsraelDateTime.ZONE)
                            .plus(Driver.RETENTION)
                            .toInstant());
                    if (retentionOver) {
                        driver.anonymize(at);
                    } else {
                        driver.anonymizeWithAccount(at);
                    }
                }
            }
        }
        if (!DriverDescription.of(driver).equals(description())) {
            throw new AssertionError("the commands reached " + DriverDescription.of(driver) + ", not " + description());
        }
        return asSaved(driver);
    }

    private static void retire(Driver driver, Instant at) {
        driver.retire(PlanUses.none(), at.atZone(IsraelDateTime.ZONE).toLocalDate(), at);
    }
}
