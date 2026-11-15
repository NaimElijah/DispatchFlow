package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.CompanyNotSetUp;
import com.dispatchflow.access.application.port.out.CompanyProfileRepository;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * The server step a named team member runs on a written request from a person the pilot agreement
 * names: it creates a new Admin with a set-password link (FR-292, FR-294). Every active Admin is
 * emailed when it runs, because an account gains the Admin role (FR-389): the use case reads them
 * through {@code UserAccountRepository.activeAdminRecipients} and sends {@code
 * EmailSender.sendAdminNotice}.
 *
 * <p>The new Admin starts in the company's default language, which the step reads from the company
 * profile (FR-182); with no profile set up yet, it throws {@code CompanyNotSetUp}.
 *
 * <p>The step reads the address at a prompt that does not echo it (FR-393), and its audit record
 * holds the account ID only (FR-409).
 *
 * <p>Built in M6.
 */
public final class CreateAdminOnWrittenRequest {

    private final UserAccountRepository accounts;
    private final CompanyProfileRepository companyProfiles;
    private final SecretTokens secretTokens;
    private final EmailSender emailSender;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the step.
     *
     * @param accounts where accounts are kept
     * @param companyProfiles where the company profile is kept, whose language a new user starts in
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param emailSender sends links and notices by email
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public CreateAdminOnWrittenRequest(
            UserAccountRepository accounts,
            CompanyProfileRepository companyProfiles,
            SecretTokens secretTokens,
            EmailSender emailSender,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.companyProfiles = companyProfiles;
        this.secretTokens = secretTokens;
        this.emailSender = emailSender;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Creates the Admin and sends the set-password link.
     *
     * <p>Built in M6.
     *
     * @param email the new Admin's address, read at a prompt that does not echo it
     * @param call the step's server context
     * @return the new account's ID
     * @throws RuleViolation with Access's {@code EmailInUse} when an account holds the address
     *     (BR-45)
     * @throws CompanyNotSetUp when no Admin has set up the company profile yet, so there is no
     *     default language (FR-182)
     */
    public AccountId create(EmailAddress email, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
