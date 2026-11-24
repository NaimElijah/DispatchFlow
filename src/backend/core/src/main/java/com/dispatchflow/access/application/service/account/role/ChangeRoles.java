package com.dispatchflow.access.application.service.account.role;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.domain.account.RoleSet;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * An Admin changes a user's roles (FR-02). The company keeps two active Admins (BR-19), and the
 * Admin whose email-change link set the account's email can't give it the Admin role; another Admin
 * can, which clears that record (ADR-116). Giving the roles it holds changes nothing. The change
 * moves the account's generation and raises {@code SessionsRevoked}, so every token issued before
 * is refused at once (NFR-90, NFR-177); the use case evicts the account from the revocation cache
 * after its commit (ADR-045). Every active Admin is emailed when it gains the Admin role (FR-389):
 * the use case reads them through {@code UserAccountRepository.activeAdminRecipients} and sends
 * {@code EmailSender.sendAdminNotice}.
 *
 * <p>The operation is Admin-only in the permission matrix, so the acting session must have given
 * its password in the last 5 minutes, which {@code Session.requireFreshStepUp} checks (NFR-214,
 * NFR-227). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p><b>The transaction.</b> {@link ChangeRolesChange} holds it: the step-up check, the change, its
 * save, the Admins' addresses and the audit record. After its commit, the use case evicts the
 * account from the revocation cache and sends the Admins' notice, so a change that rolls back sends
 * nothing (ADR-129).
 *
 * <p>Built in M6.
 */
public final class ChangeRoles {

    private final RevocationCache revocationCache;
    private final EmailSender emailSender;
    private final ChangeRolesChange change;

    /**
     * Creates the use case.
     *
     * @param revocationCache the token check's cache, evicted after a change
     * @param emailSender sends links and notices by email
     * @param change the transaction that changes the roles
     */
    public ChangeRoles(RevocationCache revocationCache, EmailSender emailSender, ChangeRolesChange change) {
        this.revocationCache = revocationCache;
        this.emailSender = emailSender;
        this.change = change;
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
