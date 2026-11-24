package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link AcknowledgeAlert} (FR-260, ADR-107): it calls {@code
 * Alert.acknowledge} with the audit record (NFR-23).
 *
 * <p>Built in M8.
 */
@Transactional
public class AcknowledgeAlertChange {

    private final AlertRepository alerts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param alerts where alerts are kept
     * @param clock the server's time
     * @param auditLog the audit trail
     */
    public AcknowledgeAlertChange(AlertRepository alerts, Clock clock, AuditLog auditLog) {
        this.alerts = alerts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Acknowledges the alert and writes the audit record.
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
    public void apply(Actor actor, AlertId alert, Version expected) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
