package com.dispatchflow.fleet.application.service.register;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.application.spi.RecordUsage;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher retires a vehicle, which is never deleted and then counts toward neither the limit
 * nor uniqueness (FR-56, FR-307, BR-43, BR-48). No uncancelled plan for today or later may use it,
 * which Planning answers through {@code RecordUsage} before the change (FR-305). Like every
 * changing call it writes its audit record, a refused attempt's in its own transaction (NFR-23,
 * ADR-110).
 *
 * <p>Built in M2.
 */
@Transactional
public class RetireVehicle {

    private final FleetRegisterRepository registers;
    private final RecordUsage recordUsage;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param registers where the fleet register is kept
     * @param recordUsage Planning's answer on which plans use a record
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RetireVehicle(FleetRegisterRepository registers, RecordUsage recordUsage, Clock clock, AuditLog auditLog) {
        this.registers = registers;
        this.recordUsage = recordUsage;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Retires the vehicle.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param vehicle the vehicle
     * @param expected the version of the register the caller last read (BR-76)
     * @throws RuleViolation with Fleet's {@code UnknownVehicle} when the register has no such
     *     vehicle; {@code VehicleNotActive} when it is already retired; {@code VehicleInUse} naming
     *     the plan that uses it (FR-305)
     * @throws NotFound when no register is stored yet
     * @throws StaleVersion when the register is no longer at {@code expected}, or changed before
     *     the save (BR-76)
     */
    public void retire(Actor actor, VehicleId vehicle, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
