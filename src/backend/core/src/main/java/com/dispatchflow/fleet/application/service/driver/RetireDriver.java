package com.dispatchflow.fleet.application.service.driver;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.fleet.application.spi.RecordUsage;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher retires a driver, whose record stays (FR-56, BR-43). No uncancelled plan for today
 * or later may use them, which Planning answers through {@code RecordUsage} before the change
 * (FR-305). {@code Driver.retire} raises {@code DriverRetired}, whose listener removes the Driver
 * role from the account, which keeps its other roles (FR-307, ADR-103). Like every changing call it
 * writes its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
public final class RetireDriver {

    private final DriverRepository drivers;
    private final RecordUsage recordUsage;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param drivers where the driver records are kept
     * @param recordUsage Planning's answer on which plans use a record
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RetireDriver(DriverRepository drivers, RecordUsage recordUsage, Clock clock, AuditLog auditLog) {
        this.drivers = drivers;
        this.recordUsage = recordUsage;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Retires the driver.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param driver the driver's account
     * @param expected the version of the driver record the caller last read (BR-76)
     * @throws RuleViolation with Fleet's {@code NotAnActiveDriver} when the driver is already
     *     retired or anonymized; {@code DriverInUse} naming the plan that uses them (FR-305)
     * @throws NotFound when no driver record has the account's ID
     * @throws StaleVersion when the driver record is no longer at {@code expected}, or changed
     *     before the save (BR-76)
     */
    public void retire(Actor actor, AccountId driver, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
