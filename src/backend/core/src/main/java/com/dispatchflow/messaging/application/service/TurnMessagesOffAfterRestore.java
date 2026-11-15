package com.dispatchflow.messaging.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * The server step a restore runs before the core serves again: messages to recipients stay off
 * until an Admin turns them on again (NFR-252, FR-350). A restored database holds its snapshot's
 * settings, which may have messages on, and the opt-outs made since the backup are not in it until
 * the runbook re-applies them (NFR-253), so a message must not go before an Admin has checked.
 *
 * <p>One transaction calls {@code MessagingSettings.changeMessages} with {@code Switch.OFF}, with
 * the audit record. Settings no Admin has set yet already mean messages are off, so it changes
 * nothing then. It runs beside Access's {@code SecureAfterRestore} in the restore runbook.
 *
 * <p>Built in M13. Rows: NFR-252, FR-350.
 */
public final class TurnMessagesOffAfterRestore {

    private final MessagingSettingsRepository settings;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the step.
     *
     * @param settings where the messaging settings are kept
     * @param clock the server's time, for the audit record
     * @param auditLog the audit trail
     */
    public TurnMessagesOffAfterRestore(MessagingSettingsRepository settings, Clock clock, AuditLog auditLog) {
        this.settings = settings;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Turns messages to recipients off.
     *
     * <p>Built in M13.
     *
     * @param call the step's server context
     * @throws StaleVersion when another writer saved the settings meanwhile; the restore runbook
     *     runs the step again
     */
    public void turnOff(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
