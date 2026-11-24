package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher acknowledges an alert, which records who did it and when; its key stays used, so the
 * same alert is never raised again (FR-260, ADR-107). {@link AcknowledgeAlertChange} holds the one
 * transaction, which calls {@code Alert.acknowledge} with the audit record. Like every changing call
 * it writes its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110). After the
 * commit, it tells the open screens through {@link LiveUpdates}.
 *
 * <p>To Be Implemented. Rows: FR-260.
 */
public final class AcknowledgeAlert {

    private final LiveUpdates liveUpdates;
    private final AcknowledgeAlertChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the open screens
     * @param change the use case's transaction
     */
    public AcknowledgeAlert(LiveUpdates liveUpdates, AcknowledgeAlertChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Acknowledges the alert.
     *
     * <p>To Be Implemented.
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
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
