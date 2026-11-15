package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.account.RoleSet;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * An Admin changes a user's roles (FR-02). The company keeps two active Admins (BR-19), and the
 * Admin whose email-change link set the account's email can't give it the Admin role; another Admin
 * can, which clears that record (ADR-116). Giving the roles it holds changes nothing. The change
 * moves the account's generation and raises {@code SessionsRevoked}, so every token issued before
 * is refused at once (NFR-90, NFR-177); the use case evicts the account from the revocation cache
 * after its commit (ADR-045). Every active Admin is emailed when it gains the Admin role (FR-389).
 *
 * <p>The operation is Admin-only in the permission matrix, so the acting session must have given
 * its password in the last 5 minutes, which {@code Session.requireFreshStepUp} checks (NFR-214,
 * NFR-227). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M6.
 */
public final class ChangeRoles {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final RevocationCache revocationCache;
    private final EmailSender emailSender;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     * @param revocationCache the token check's cache, evicted after a change
     * @param emailSender sends links and notices by email
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ChangeRoles(
            UserAccountRepository accounts,
            SessionRepository sessions,
            RevocationCache revocationCache,
            EmailSender emailSender,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.revocationCache = revocationCache;
        this.emailSender = emailSender;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the roles.
     *
     * <p>Built in M6.
     *
     * @param actor the signed-in user acting
     * @param account the user's account
     * @param roles the roles it will hold, at least one
     * @param expected the version of the account the caller last read (BR-76)
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code NoRoleGiven} for no role (FR-02); {@code TooFewAdmins} when
     *     the company would have fewer than two active Admins (BR-19); {@code
     *     EmailSetByAnotherAccount} when the acting Admin's link set the account's email and the
     *     change gives it the Admin role (ADR-116); {@code StepUpRequired} when the acting session
     *     gave its password more than 5 minutes ago, or {@code SessionRevoked} when it is revoked
     *     (NFR-214)
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void change(Actor actor, AccountId account, RoleSet roles, Version expected) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
