package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link DropOldLinks} (ADR-129): {@link #due} lists the accounts holding an
 * old link, read-only, and {@link #apply} is one account's transaction: it loads the account, drops
 * its links sent more than {@code UserAccount.LINK_KEPT_FOR} before the clock's instant, saves it,
 * and writes its audit record naming the account (NFR-93).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
@Transactional
public class DropOldLinksChange {

    private final UserAccountRepository accounts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param accounts where accounts are kept
     * @param clock the time the cutoff counts back from
     * @param auditLog where the audit records are written
     */
    public DropOldLinksChange(UserAccountRepository accounts, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The accounts holding a link sent more than {@code UserAccount.LINK_KEPT_FOR} before the
     * clock's instant, through {@code UserAccountRepository.withLinksSentBefore}.
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
     * Drops one account's old links, and saves it.
     *
     * <p>Built in M13.
     *
     * @param account an account {@link #due} listed
     * @param call the job's server step
     * @return whether the account dropped any link; {@code false} when a new link dropped them
     *     after the job listed it
     * @throws StaleVersion when the account changed between its read and its save (BR-76); the job
     *     leaves it for the next night
     */
    public boolean apply(AccountId account, CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
