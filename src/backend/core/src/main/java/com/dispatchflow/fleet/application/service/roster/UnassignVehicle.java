package com.dispatchflow.fleet.application.service.roster;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher records that a rostered driver drives no vehicle on a date; they still work their
 * shift (FR-54). The choice counts as set, so a fill leaves it alone (FR-79). A date with no roster
 * yet is opened empty, never saved, so the domain answers a driver who is not on it.
 * Like every changing call it writes its audit record, a refused attempt's in its own transaction
 * (NFR-23, ADR-110).
 *
 * <p>Built in M2.
 */
@Transactional
public class UnassignVehicle {

    private final DayRosterRepository rosters;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param rosters where the day rosters are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public UnassignVehicle(DayRosterRepository rosters, Clock clock, AuditLog auditLog) {
        this.rosters = rosters;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Takes the vehicle off the driver.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @param date the roster's date
     * @param driver the driver's account
     * @param expected the version of the date's roster the caller last read, or empty for a date
     *     with none yet, which sends {@code If-None-Match: *} (BR-76)
     * @throws RuleViolation with Fleet's {@code DriverNotRostered} when the driver has no shift
     *     that date
     * @throws StaleVersion when the roster is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void unassign(Actor actor, DeliveryDate date, AccountId driver, Optional<Version> expected) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
