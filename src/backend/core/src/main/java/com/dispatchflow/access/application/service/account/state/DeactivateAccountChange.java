package com.dispatchflow.access.application.service.account.state;

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
 * The transaction of {@link DeactivateAccount} (ADR-129): it checks the acting session's step-up,
 * deactivates the account, saves it, and writes the audit record (NFR-23). The use case evicts the
 * account from the revocation cache after the commit.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class DeactivateAccountChange {

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
    public DeactivateAccountChange(
            UserAccountRepository accounts, SessionRepository sessions, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Deactivates the account, and saves it.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param account the user's account
     * @param expected the version of the account the caller last read (BR-76)
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code TooFewAdmins} when it is one of two or fewer active Admins
     *     (FR-174, BR-19); {@code StepUpRequired} when the acting session gave its password more
     *     than 5 minutes ago, or {@code SessionRevoked} when it is revoked (NFR-214)
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void apply(Actor actor, AccountId account, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
