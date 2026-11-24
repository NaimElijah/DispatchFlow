package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.CompanyNotSetUp;
import com.dispatchflow.access.application.port.out.CompanyProfileRepository;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.RoleSet;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link OpenAccount} (ADR-129): it checks the acting session's step-up, reads
 * the company's default language, creates the account with its set-password link, adds it, reads
 * the Admins' addresses through {@code UserAccountRepository.activeAdminRecipients} when it opens
 * with the Admin role (FR-389), and writes the audit record (NFR-23). The use case sends the emails
 * after the commit.
 *
 * <p>Built in M6.
 */
@Transactional
public class OpenAccountChange {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final CompanyProfileRepository companyProfiles;
    private final SecretTokens secretTokens;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     * @param companyProfiles where the company profile is kept, whose language a new user starts in
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public OpenAccountChange(
            UserAccountRepository accounts,
            SessionRepository sessions,
            CompanyProfileRepository companyProfiles,
            SecretTokens secretTokens,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.companyProfiles = companyProfiles;
        this.secretTokens = secretTokens;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Opens the account with its set-password link.
     *
     * <p>Built in M6.
     *
     * @param actor the signed-in user acting
     * @param email the address the user signs in with
     * @param roles the user's roles, at least one
     * @return the new account, its link's email, and the notice every active Admin gets when it
     *     opens with the Admin role
     * @throws RuleViolation with Access's {@code NoRoleGiven} for no role (FR-02); {@code
     *     EmailInUse} when an account holds the address (BR-45); {@code StepUpRequired} when the
     *     acting session gave its password more than 5 minutes ago, or {@code SessionRevoked} when
     *     it is revoked (NFR-214)
     * @throws CompanyNotSetUp when no Admin has set up the company profile yet, so there is no
     *     default language (FR-182)
     */
    public OpenedAccount apply(Actor actor, EmailAddress email, RoleSet roles) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
