package com.dispatchflow.fleet.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.driver.DriverContact;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;

/**
 * A dispatcher changes an active driver's name, phone and defaults (FR-06, FR-56, FR-189). A change
 * applies to dates filled later, and never to a value a roster already holds (FR-79). Like every
 * changing call it writes its audit record, a refused attempt's in its own transaction (NFR-23,
 * ADR-110).
 *
 * <p>Built in M2.
 */
public final class ChangeDriverDetails {

    private final DriverRepository drivers;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param drivers where the driver records are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ChangeDriverDetails(DriverRepository drivers, Clock clock, AuditLog auditLog) {
        this.drivers = drivers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the driver's details.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param command the driver and their new details
     * @param expected the version of the driver record the caller last read (BR-76)
     * @throws RuleViolation with Fleet's {@code NotAnActiveDriver} when the driver is retired or
     *     anonymized
     * @throws NotFound when no driver record has the account's ID
     * @throws StaleVersion when the driver record is no longer at {@code expected}, or changed
     *     before the save (BR-76)
     */
    public void change(Actor actor, Command command, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * A driver record's details as a dispatcher saves them (FR-06, FR-189).
     *
     * <p>Built in M2.
     *
     * @param driver the driver's account
     * @param contact their name and phone
     * @param defaultShift the shift hours a date gets by default
     * @param defaultVehicle the vehicle a date gets by default
     */
    public record Command(AccountId driver, DriverContact contact, ShiftHours defaultShift, VehicleId defaultVehicle)
            implements PersonalData {

        /** Checks every part is present. */
        public Command {
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(contact, "contact");
            Objects.requireNonNull(defaultShift, "defaultShift");
            Objects.requireNonNull(defaultVehicle, "defaultVehicle");
        }

        /**
         * A form that hides the name and the phone.
         *
         * @return {@code ChangeDriverDetails.Command[redacted]}
         */
        @Override
        public String toString() {
            return "ChangeDriverDetails.Command[redacted]";
        }
    }
}
