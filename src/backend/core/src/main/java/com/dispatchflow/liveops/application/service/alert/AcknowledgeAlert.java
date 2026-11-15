package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher acknowledges an alert, which records who did it and when; its key stays used, so the
 * same alert is never raised again (FR-260, ADR-107). One transaction calls {@code
 * Alert.acknowledge} with the audit record. Like every changing call it writes its audit record, a
 * refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M8. Rows: FR-260.
 */
public final class AcknowledgeAlert {

    private final AlertRepository alerts;
    private final Clock clock;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param alerts where alerts are kept
     * @param clock the server's time
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public AcknowledgeAlert(AlertRepository alerts, Clock clock, AuditLog auditLog, LiveUpdates liveUpdates) {
        this.alerts = alerts;
        this.clock = clock;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Acknowledges the alert.
     *
     * <p>Built in M8.
     *
     * @param actor the dispatcher
     * @param alert the alert
     * @param expected the alert's version the dispatcher saw (BR-76)
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     AlertAlreadyAcknowledged} when it was acknowledged before
     * @throws NotFound when no alert has the ID
     * @throws StaleVersion when the alert changed since {@code expected}
     */
    public void acknowledge(Actor actor, AlertId alert, Version expected) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
