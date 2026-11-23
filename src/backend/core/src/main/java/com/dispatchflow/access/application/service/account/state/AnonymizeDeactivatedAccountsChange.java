package com.dispatchflow.access.application.service.account.state;

import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link AnonymizeDeactivatedAccounts} (ADR-129): {@link #due} lists the
 * accounts whose retention is over, read-only, and {@link #apply} is one account's transaction: it
 * loads the account, anonymizes it through {@code UserAccount.anonymize} while its retention is
 * still over, saves it, and writes its audit record (NFR-23, NFR-258).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
@Transactional
public class AnonymizeDeactivatedAccountsChange {

    private final UserAccountRepository accounts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param accounts where accounts are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public AnonymizeDeactivatedAccountsChange(UserAccountRepository accounts, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The accounts deactivated a year or more before the clock's instant, in Israel's calendar, and
     * not yet anonymized, through {@code UserAccountRepository.deactivatedBefore} (NFR-258).
     *
     * <p>Built in M13.
     *
     * @return their IDs, in any order
     */
    @Transactional(readOnly = true)
    public List<AccountId> due() {
        throw new UnsupportedOperationException("Built in M13");
    }

    /**
     * Anonymizes one account, and saves it.
     *
     * <p>Built in M13.
     *
     * @param account an account {@link #due} listed
     * @param call the job's server step
     * @return whether the account was anonymized; {@code false} when it is no longer due, as when an
     *     Admin reactivated it after the job listed it
     * @throws StaleVersion when the account changed between its read and its save (BR-76); the job
     *     leaves it for the next night
     */
    public boolean apply(AccountId account, CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
