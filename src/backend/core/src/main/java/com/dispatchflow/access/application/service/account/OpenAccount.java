package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.CompanyNotSetUp;
import com.dispatchflow.access.application.port.out.CompanyProfileRepository;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.RoleSet;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * An Admin opens a user's account with one or more roles (FR-02, FR-293), and it gets a
 * set-password link at its address, its only way to a first password (FR-294). It starts in the
 * company's default language, which the use case reads from the company profile (FR-182); with no
 * profile set up yet, it throws {@code CompanyNotSetUp}. An address another account holds is
 * refused (BR-45). Every active Admin is emailed when it opens with the Admin role (FR-389): the
 * use case reads them through {@code UserAccountRepository.activeAdminRecipients} and sends {@code
 * EmailSender.sendAdminNotice}.
 *
 * <p>The operation is Admin-only in the permission matrix, so the acting session must have given
 * its password in the last 5 minutes, which {@code Session.requireFreshStepUp} checks (NFR-214,
 * NFR-227). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M6.
 */
public final class OpenAccount {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final CompanyProfileRepository companyProfiles;
    private final SecretTokens secretTokens;
    private final EmailSender emailSender;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     * @param companyProfiles where the company profile is kept, whose language a new user starts in
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param emailSender sends links and notices by email
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public OpenAccount(
            UserAccountRepository accounts,
            SessionRepository sessions,
            CompanyProfileRepository companyProfiles,
            SecretTokens secretTokens,
            EmailSender emailSender,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.companyProfiles = companyProfiles;
        this.secretTokens = secretTokens;
        this.emailSender = emailSender;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Opens the account and sends its set-password link.
     *
     * <p>Built in M6.
     *
     * @param actor the signed-in user acting
     * @param email the address the user signs in with
     * @param roles the user's roles, at least one
     * @return the new account's ID
     * @throws RuleViolation with Access's {@code NoRoleGiven} for no role (FR-02); {@code
     *     EmailInUse} when an account holds the address (BR-45); {@code StepUpRequired} when the
     *     acting session gave its password more than 5 minutes ago, or {@code SessionRevoked} when
     *     it is revoked (NFR-214)
     * @throws CompanyNotSetUp when no Admin has set up the company profile yet, so there is no
     *     default language (FR-182)
     */
    public AccountId open(Actor actor, EmailAddress email, RoleSet roles) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
