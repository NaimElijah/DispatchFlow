package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.Clock;

/**
 * The nightly retention job's account step: it anonymizes in place every account deactivated a year
 * ago or more, in Israel's calendar, whatever its roles (NFR-258, NFR-260, ADR-075). {@code
 * UserAccount.anonymize} blanks the email, the password hash and the links, keeps the record, and
 * raises {@code AccountAnonymized}, so Fleet anonymizes the driver record (ADR-104).
 *
 * <p>Each account is anonymized in its own transaction, so one stale save doesn't hold back the
 * rest; the next night retries it.
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
public final class AnonymizeDeactivatedAccounts {

    private final UserAccountRepository accounts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param accounts where accounts are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public AnonymizeDeactivatedAccounts(UserAccountRepository accounts, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Anonymizes the accounts whose retention is over.
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many accounts were anonymized
     */
    public int anonymizeDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
