package com.dispatchflow.fleet.application.service.register;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.domain.register.VehicleDetails;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher registers a vehicle with its capacities, its start and end depots, a display name
 * and a plate (FR-05, FR-131, FR-198). The register holds at most 15 active vehicles (BR-36), and
 * their names and plates are unique (BR-48). Like every changing call it writes its audit record, a
 * refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class AddVehicle {

    private final FleetRegisterRepository registers;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param registers where the fleet register is kept
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public AddVehicle(FleetRegisterRepository registers, IdGenerator ids, Clock clock, AuditLog auditLog) {
        this.registers = registers;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Registers the vehicle.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param details its name, plate, capacity and depots
     * @param expected the version of the register the caller last read (BR-76)
     * @return the new vehicle's ID
     * @throws RuleViolation with Fleet's {@code FleetFull} when 15 vehicles are active (BR-36);
     *     {@code UnknownDepot} when either depot is not active (BR-40); {@code
     *     DuplicateVehicleName} or {@code DuplicatePlate} when an active vehicle has the name or
     *     the plate (BR-48)
     * @throws NotFound when no register is stored yet
     * @throws StaleVersion when the register is no longer at {@code expected}, or changed before
     *     the save (BR-76)
     */
    public VehicleId add(Actor actor, VehicleDetails details, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
