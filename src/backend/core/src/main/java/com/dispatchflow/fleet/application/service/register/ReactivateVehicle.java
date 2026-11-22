package com.dispatchflow.fleet.application.service.register;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher makes a retired vehicle active again, so new plans may use it (FR-193). It counts
 * again, so the limit, its depots and uniqueness are checked as for a new one (BR-36, BR-40,
 * BR-48). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class ReactivateVehicle {

    private final FleetRegisterRepository registers;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param registers where the fleet register is kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ReactivateVehicle(FleetRegisterRepository registers, Clock clock, AuditLog auditLog) {
        this.registers = registers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Reactivates the vehicle.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param vehicle the vehicle
     * @param expected the version of the register the caller last read (BR-76)
     * @throws RuleViolation with Fleet's {@code UnknownVehicle} when the register has no such
     *     vehicle; {@code FleetFull} when 15 vehicles are active (BR-36); {@code UnknownDepot} when
     *     one of its depots is retired (BR-40); {@code DuplicateVehicleName} or {@code
     *     DuplicatePlate} when an active vehicle now has its name or plate (BR-48)
     * @throws NotFound when no register is stored yet
     * @throws StaleVersion when the register is no longer at {@code expected}, or changed before
     *     the save (BR-76)
     */
    public void reactivate(Actor actor, VehicleId vehicle, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
