package com.dispatchflow.fleet.application.service.register;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.domain.register.DepotHours;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * A dispatcher sets or clears an active depot's opening hours, one pair for every date (FR-129,
 * BR-46, ADR-103). Like every changing call it writes its audit record, a refused attempt's in its
 * own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class ChangeDepotHours {

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
    public ChangeDepotHours(FleetRegisterRepository registers, Clock clock, AuditLog auditLog) {
        this.registers = registers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the hours.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param depot the depot
     * @param hours the new hours, or empty for none
     * @param expected the version of the register the caller last read (BR-76)
     * @throws RuleViolation with Fleet's {@code UnknownDepot} when no active depot has the ID
     * @throws NotFound when no register is stored yet
     * @throws StaleVersion when the register is no longer at {@code expected}, or changed before
     *     the save (BR-76)
     */
    public void change(Actor actor, DepotId depot, Optional<DepotHours> hours, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
