package com.dispatchflow.fleet.application.service.driver;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.fleet.domain.driver.WeeklyShifts;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher replaces an active driver's weekly shift template, which fills the per-date values
 * nobody has set (FR-79, ADR-103). The empty template leaves every date to the default shift. Like
 * every changing call it writes its audit record, a refused attempt's in its own transaction
 * (NFR-23, ADR-110).
 *
 * <p>Built in an unscheduled milestone: the Alpha leaves weekly shifts out and seeds its rosters
 * (DL §4.2), and the roadmap names no milestone for them.
 */
@Transactional
public class ChangeWeeklyShifts {

    private final DriverRepository drivers;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param drivers where the driver records are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ChangeWeeklyShifts(DriverRepository drivers, Clock clock, AuditLog auditLog) {
        this.drivers = drivers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Replaces the template.
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param actor the signed-in user acting
     * @param driver the driver's account
     * @param weeklyShifts the new template
     * @param expected the version of the driver record the caller last read (BR-76)
     * @throws RuleViolation with Fleet's {@code NotAnActiveDriver} when the driver is retired or
     *     anonymized
     * @throws NotFound when no driver record has the account's ID
     * @throws StaleVersion when the driver record is no longer at {@code expected}, or changed
     *     before the save (BR-76)
     */
    public void change(Actor actor, AccountId driver, WeeklyShifts weeklyShifts, Version expected) {
        throw new UnsupportedOperationException("Built in an unscheduled milestone");
    }
}
