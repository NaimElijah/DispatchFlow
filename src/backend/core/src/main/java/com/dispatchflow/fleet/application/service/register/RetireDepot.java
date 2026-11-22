package com.dispatchflow.fleet.application.service.register;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.application.spi.RecordUsage;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher retires a depot, which is never deleted (FR-56, BR-43). No active vehicle may start
 * or end there (BR-40), and no uncancelled plan for today or later may use it, which Planning
 * answers through {@code RecordUsage} before the change (FR-305). Like every changing call it
 * writes its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class RetireDepot {

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
    public RetireDepot(FleetRegisterRepository registers, RecordUsage recordUsage, Clock clock, AuditLog auditLog) {
        this.registers = registers;
        this.recordUsage = recordUsage;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Retires the depot.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param depot the depot
     * @param expected the version of the register the caller last read (BR-76)
     * @throws RuleViolation with Fleet's {@code UnknownDepot} when no active depot has the ID;
     *     {@code DepotHasActiveVehicles} when active vehicles use it (BR-40); {@code DepotInUse}
     *     naming the plan that uses it (FR-305)
     * @throws NotFound when no register is stored yet
     * @throws StaleVersion when the register is no longer at {@code expected}, or changed before
     *     the save (BR-76)
     */
    public void retire(Actor actor, DepotId depot, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
