package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link EndEverySession} (ADR-129): it checks the acting session's step-up,
 * ends the account's sessions through the account, saves it, and writes the audit record (NFR-23).
 * The use case evicts the account from the revocation cache after the commit.
 *
 * <p>Built in M6.
 */
@Transactional
public class EndEverySessionChange {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public EndEverySessionChange(
            UserAccountRepository accounts, SessionRepository sessions, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Ends the sessions, and saves the account.
     *
     * <p>Built in M6.
     *
     * @param actor the signed-in user acting
     * @param account the user's account
     * @param expected the version of the account the caller last read (BR-76)
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code StepUpRequired} when the acting session gave its password
     *     more than 5 minutes ago, or {@code SessionRevoked} when it is revoked (NFR-214)
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void apply(Actor actor, AccountId account, Version expected) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
