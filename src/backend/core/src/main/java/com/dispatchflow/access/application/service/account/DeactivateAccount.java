package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * An Admin deactivates a user: sign-in stops, every session ends, every open link is voided, and
 * their history stays (FR-53, NFR-224). An access token issued before is refused at once (FR-173).
 * An Admin can't be deactivated while the company has two or fewer active Admins (FR-174, BR-19).
 * An Admin reactivates the account (FR-297). The change moves the account's generation and raises
 * {@code SessionsRevoked}, so every token issued before is refused at once (NFR-90, NFR-177); the
 * use case evicts the account from the revocation cache after its commit (ADR-045).
 *
 * <p>The operation is Admin-only in the permission matrix, so the acting session must have given
 * its password in the last 5 minutes, which {@code Session.requireFreshStepUp} checks (NFR-214,
 * NFR-227). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M6.
 */
public final class DeactivateAccount {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final RevocationCache revocationCache;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     * @param revocationCache the token check's cache, evicted after a change
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public DeactivateAccount(
            UserAccountRepository accounts,
            SessionRepository sessions,
            RevocationCache revocationCache,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.revocationCache = revocationCache;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Deactivates the account.
     *
     * <p>Built in M6.
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
    public void deactivate(Actor actor, AccountId account, Version expected) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
