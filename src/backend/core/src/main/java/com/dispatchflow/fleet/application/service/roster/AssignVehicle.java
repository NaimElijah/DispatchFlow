package com.dispatchflow.fleet.application.service.roster;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher gives a rostered driver a vehicle for a date (FR-54). On the date a driver drives at
 * most one vehicle and a vehicle has at most one driver; both are active (FR-190). Giving the
 * vehicle they already drive changes nothing. A date with no roster yet is opened empty, never saved,
 * so the domain answers a driver who is not on it.
 *
 * <p>The driver's standing combines Access's answer on the account with the driver record's own
 * state, read now (ADR-062). The vehicle's standing is the fleet register's. Like every changing
 * call it writes its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
@Transactional
public class AssignVehicle {

    private final DayRosterRepository rosters;
    private final DriverRepository drivers;
    private final FleetRegisterRepository registers;
    private final Accounts accounts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param rosters where the day rosters are kept
     * @param drivers where the driver records are kept
     * @param registers where the fleet register is kept
     * @param accounts Access's answer on whether an account is an active driver
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public AssignVehicle(
            DayRosterRepository rosters,
            DriverRepository drivers,
            FleetRegisterRepository registers,
            Accounts accounts,
            Clock clock,
            AuditLog auditLog) {
        this.rosters = rosters;
        this.drivers = drivers;
        this.registers = registers;
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Assigns the vehicle.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param date the roster's date
     * @param driver the driver's account
     * @param vehicle the vehicle
     * @param expected the version of the date's roster the caller last read, or empty for a date
     *     with none yet, which sends {@code If-None-Match: *} (BR-76)
     * @throws RuleViolation with Fleet's {@code NotAnActiveDriver} or {@code VehicleNotActive} when
     *     either is not active; {@code DriverNotRostered} when the driver has no shift that date;
     *     {@code VehicleAlreadyAssigned} when another driver drives the vehicle; {@code
     *     DriverAlreadyHasVehicle} when the driver drives another vehicle
     * @throws NotFound when no account has the driver's ID
     * @throws StaleVersion when the roster is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void assign(
            Actor actor, DeliveryDate date, AccountId driver, VehicleId vehicle, Optional<Version> expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
