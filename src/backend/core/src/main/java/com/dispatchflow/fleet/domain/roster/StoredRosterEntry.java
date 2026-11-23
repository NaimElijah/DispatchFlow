package com.dispatchflow.fleet.domain.roster;

import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The columns one {@link RosterEntry} is stored in, a row of its {@link DayRoster}'s entry table
 * (ADR-127 decision 3). An entry has no ID of its own and its vehicle is a sealed state, so it is
 * stored as this embeddable, with the vehicle as a kind and a nullable vehicle; {@link #entry()}
 * builds the public record every caller sees. Only {@code DayRoster} makes one.
 */
@Embeddable
class StoredRosterEntry {

    /** Which {@link RosterVehicle} an entry holds. */
    private enum VehicleChoice {
        NOT_SET,
        NO_VEHICLE,
        ASSIGNED
    }

    private AccountId driver;

    private DeliveryDate shiftDate;

    @Embedded
    private ShiftHours shiftHours;

    @Enumerated(EnumType.STRING)
    private VehicleChoice vehicleChoice;

    private @Nullable VehicleId vehicle;

    @Enumerated(EnumType.STRING)
    private Availability availability;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredRosterEntry() {}

    /**
     * Stores an entry.
     *
     * @param entry the entry
     */
    StoredRosterEntry(RosterEntry entry) {
        this.driver = entry.driver();
        this.shiftDate = entry.shift().date();
        this.shiftHours = entry.shift().hours();
        this.vehicle = entry.vehicle().driven().orElse(null);
        this.vehicleChoice = switch (entry.vehicle()) {
            case RosterVehicle.NotSet notSet -> VehicleChoice.NOT_SET;
            case RosterVehicle.NoVehicle noVehicle -> VehicleChoice.NO_VEHICLE;
            case RosterVehicle.Assigned assigned -> VehicleChoice.ASSIGNED;
        };
        this.availability = entry.availability();
    }

    /**
     * The driver the entry is for.
     *
     * @return the driver
     */
    AccountId driver() {
        return driver;
    }

    /**
     * The entry these columns store.
     *
     * @return the entry
     * @throws StoredStateInvalid when its shift falls in an hour the clock skips on its date, which a
     *     change to the time-zone rules can do to a stored shift
     */
    RosterEntry entry() {
        Shift shift = StoredStateInvalid.whileRestoring(Shift.class, () -> Shift.on(shiftDate, shiftHours));
        RosterVehicle chosen = switch (vehicleChoice) {
            case NOT_SET -> new RosterVehicle.NotSet();
            case NO_VEHICLE -> new RosterVehicle.NoVehicle();
            case ASSIGNED ->
                new RosterVehicle.Assigned(Objects.requireNonNull(vehicle, "an assigned entry has a vehicle"));
        };
        return new RosterEntry(driver, shift, chosen, availability);
    }
}
