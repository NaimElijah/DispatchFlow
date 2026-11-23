package com.dispatchflow.access.application.service.account.state;

import com.dispatchflow.audit.application.api.CallContext;

/**
 * The nightly retention job's account step: it anonymizes in place every account deactivated a year
 * ago or more, in Israel's calendar, whatever its roles (NFR-258, NFR-260, ADR-075). {@code
 * UserAccount.anonymize} blanks the email, the password hash and the links, keeps the record, and
 * raises {@code AccountAnonymized}, so Fleet anonymizes the driver record (ADR-104).
 *
 * <p>Each account is anonymized in its own transaction, so one stale save doesn't hold back the
 * rest; the next night retries it.
 *
 * <p><b>The transactions.</b> The job lists the due accounts through {@link
 * AnonymizeDeactivatedAccountsChange#due}, a read-only transaction, then hands each to {@link
 * AnonymizeDeactivatedAccountsChange#apply}, one account's transaction (ADR-129). A {@code
 * StaleVersion} from one account is left for the next night, and the job goes on to the next.
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
public final class AnonymizeDeactivatedAccounts {

    private final AnonymizeDeactivatedAccountsChange change;

    /**
     * Creates the job.
     *
     * @param change the transactions that list the due accounts and anonymize one
     */
    public AnonymizeDeactivatedAccounts(AnonymizeDeactivatedAccountsChange change) {
        this.change = change;
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
