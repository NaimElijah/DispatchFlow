package com.dispatchflow.fleet.application.service;

import com.dispatchflow.access.events.AccountAnonymized;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Fleet's listener on Access's {@code AccountAnonymized}: it removes the driver record's name and
 * phone through {@code Driver.anonymizeWithAccount}, whatever the record's state, and keeps the
 * record, because plans refer to it (NFR-258, NFR-260, ADR-103). An account with no driver record
 * changes nothing, and so does a redelivered event (ADR-064). It writes its audit record with the
 * server step's context, because ADR-110 audits server steps.
 *
 * <p>It follows Access's {@code AnonymizeDeactivatedAccounts}, the only raiser of the event, into
 * M13.
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
public final class AnonymizeDriverWithAccount {

    private final DriverRepository drivers;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the listener.
     *
     * @param drivers where the driver records are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public AnonymizeDriverWithAccount(DriverRepository drivers, Clock clock, AuditLog auditLog) {
        this.drivers = drivers;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Anonymizes the driver record.
     *
     * <p>Built in M13.
     *
     * @param event the account's anonymization
     * @param call the listener's server step
     * @throws StaleVersion when the record changed between its read and its save; the listener
     *     adapter retries at once, up to three times (ADR-120 decision 7, DL §11)
     */
    public void on(AccountAnonymized event, CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
