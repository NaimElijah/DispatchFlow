package com.dispatchflow.fleet.domain.driver;

import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A driver's stored state: what {@link Driver#snapshot()} returns and {@link Driver#restore}
 * rebuilds from. It holds the driver's name and phone until they are anonymized, so it is personal
 * data (ADR-106), and {@link #toString()} leaves the contact out.
 *
 * <p>The persistence adapter builds every stored value inside {@link
 * com.dispatchflow.shared.StoredStateInvalid#whileRestoring}, so a value that the rules now refuse
 * is an incident, never a user-facing refusal. Here that covers the default shift and the weekly
 * shifts, and the name and phone, which {@link DriverName#ofStored} and {@code
 * PhoneNumber.ofStored} rebuild. A value has an {@code ofStored} factory where the stored rule
 * differs from the input rule, as {@code PhoneNumber.ofStored} accepts any E.164 text, or where it
 * already wraps the input rule this way, as the names' factories do.
 *
 * @param id the driver's account, which keys the driver
 * @param version the version it was loaded at, or null for an aggregate never saved
 *     (ADR-126)
 * @param status active, retired or anonymized
 * @param contact the driver's name and phone; empty only once anonymized
 * @param defaultShift the shift hours a date gets by default (FR-06, FR-189)
 * @param defaultVehicle the vehicle a date gets by default (FR-06, FR-189)
 * @param weeklyShifts the weekly template, which may be empty (FR-79)
 */
public record DriverSnapshot(
        AccountId id,
        @Nullable Version version,
        DriverStatus status,
        Optional<DriverContact> contact,
        ShiftHours defaultShift,
        VehicleId defaultVehicle,
        WeeklyShifts weeklyShifts)
        implements PersonalData {

    /** Checks every field is present. */
    public DriverSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(contact, "contact");
        Objects.requireNonNull(defaultShift, "defaultShift");
        Objects.requireNonNull(defaultVehicle, "defaultVehicle");
        Objects.requireNonNull(weeklyShifts, "weeklyShifts");
    }

    /**
     * A form that names the driver by account and state and leaves their name and phone out.
     *
     * @return the snapshot without its contact
     */
    @Override
    public String toString() {
        return "DriverSnapshot[id=" + id + ", version=" + version + ", status=" + status + ", contact="
                + (contact.isPresent() ? "redacted" : "none") + ", defaultShift=" + defaultShift
                + ", defaultVehicle=" + defaultVehicle + ", weeklyShifts=" + weeklyShifts + "]";
    }
}
