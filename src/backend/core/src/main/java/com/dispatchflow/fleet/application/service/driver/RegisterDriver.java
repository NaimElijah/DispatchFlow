package com.dispatchflow.fleet.application.service.driver;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.driver.DriverContact;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher registers a driver for an account that is active and holds the Driver role, with
 * their name and phone, their default shift and their default vehicle, which are the default for a
 * date (FR-06, FR-132, FR-189). Whether the vehicle is active is not checked here: a fill skips a
 * driver whose default vehicle is not.
 *
 * <p>Fleet has no error yet for an account that already has a driver record; M2 adds it. Like every
 * changing call it writes its audit record, a refused attempt's in its own transaction (NFR-23,
 * ADR-110).
 *
 * <p>Built in M2.
 */
@Transactional
public class RegisterDriver {

    private final DriverRepository drivers;
    private final Accounts accounts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param drivers where the driver records are kept
     * @param accounts Access's answer on whether an account is an active driver
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RegisterDriver(DriverRepository drivers, Accounts accounts, Clock clock, AuditLog auditLog) {
        this.drivers = drivers;
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Registers the driver.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param driver the account the record is keyed by
     * @param contact their name and phone
     * @param defaultShift the shift hours a date gets by default
     * @param defaultVehicle the vehicle a date gets by default
     * @throws RuleViolation with Fleet's {@code NotAnActiveDriver} when the account is not active
     *     or holds no Driver role
     * @throws NotFound when no account has the ID
     */
    public void register(
            Actor actor, AccountId driver, DriverContact contact, ShiftHours defaultShift, VehicleId defaultVehicle) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
