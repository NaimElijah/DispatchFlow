package com.dispatchflow.fleet.application.service.register;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.domain.register.VehicleDetails;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher changes an active vehicle's details, which later plans use (FR-05, FR-306). Names
 * and plates stay unique among active vehicles (BR-48). Like every changing call it writes its
 * audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
@Transactional
public class ChangeVehicle {

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
    public ChangeVehicle(FleetRegisterRepository registers, Clock clock, AuditLog auditLog) {
        this.registers = registers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the vehicle.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param vehicle the vehicle
     * @param details its new name, plate, capacity and depots
     * @param expected the version of the register the caller last read (BR-76)
     * @throws RuleViolation with Fleet's {@code UnknownVehicle} when the register has no such
     *     vehicle; {@code VehicleNotActive} when it is retired; {@code UnknownDepot} when either
     *     depot is not active (BR-40); {@code DuplicateVehicleName} or {@code DuplicatePlate} when
     *     another active vehicle has the name or the plate (BR-48)
     * @throws NotFound when no register is stored yet
     * @throws StaleVersion when the register is no longer at {@code expected}, or changed before
     *     the save (BR-76)
     */
    public void change(Actor actor, VehicleId vehicle, VehicleDetails details, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
