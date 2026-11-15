package com.dispatchflow.fleet.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.domain.register.DepotHours;
import com.dispatchflow.fleet.domain.register.DepotName;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * A dispatcher adds a depot (FR-04), which may have no location yet, and then starts and ends no
 * route (FR-197), and may have opening hours that routes keep to (FR-129, BR-46). Setting a
 * location by address waits for the geocoding port M7 settles (FR-130, placement P4). Before any
 * depot exists the register starts from {@code FleetRegister.empty()}. Like every changing call it
 * writes its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class AddDepot {

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
    public AddDepot(FleetRegisterRepository registers, IdGenerator ids, Clock clock, AuditLog auditLog) {
        this.registers = registers;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Adds the depot.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param name the depot's name
     * @param location its location, if known (FR-130)
     * @param hours its opening hours, if it has any (FR-129)
     * @param expected the version of the register the caller last read (BR-76)
     * @return the new depot's ID
     * @throws StaleVersion when the register is no longer at {@code expected}, or changed before
     *     the save (BR-76)
     */
    public DepotId add(
            Actor actor, DepotName name, Optional<GeoPoint> location, Optional<DepotHours> hours, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
