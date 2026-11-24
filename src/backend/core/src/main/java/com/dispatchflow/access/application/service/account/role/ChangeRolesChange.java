package com.dispatchflow.access.application.service.account.role;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.AdminNotice;
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
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link ChangeRoles} (ADR-129): it checks the acting session's step-up, changes
 * the roles through the account, saves it, reads the Admins' addresses through {@code
 * UserAccountRepository.activeAdminRecipients} when it gains the Admin role (FR-389), and writes the
 * audit record (NFR-23). The use case evicts the account and sends the notice after the commit.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class ChangeRolesChange {

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
    public ChangeRolesChange(
            UserAccountRepository accounts, SessionRepository sessions, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the roles, and saves the account.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param account the user's account
     * @param roles the roles it will hold, at least one
     * @param expected the version of the account the caller last read (BR-76)
     * @return the notice every active Admin gets when the account gains the Admin role; otherwise
     *     empty
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
    public Optional<AdminNotice> apply(Actor actor, AccountId account, RoleSet roles, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
