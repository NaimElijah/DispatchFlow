package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.CompanyNotSetUp;
import com.dispatchflow.access.application.port.out.CompanyProfileRepository;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link CreateAdminOnWrittenRequest} (ADR-129): it reads the company's default
 * language, creates the Admin's account with its set-password link, adds it, reads the Admins'
 * addresses through {@code UserAccountRepository.activeAdminRecipients} (FR-389), and writes the
 * audit record, which holds the account ID only (FR-409). The step sends the emails after the
 * commit.
 *
 * <p>Built in M6.
 */
@Transactional
public class CreateAdminOnWrittenRequestChange {

    private final UserAccountRepository accounts;
    private final CompanyProfileRepository companyProfiles;
    private final SecretTokens secretTokens;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param companyProfiles where the company profile is kept, whose language a new user starts in
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public CreateAdminOnWrittenRequestChange(
            UserAccountRepository accounts,
            CompanyProfileRepository companyProfiles,
            SecretTokens secretTokens,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.companyProfiles = companyProfiles;
        this.secretTokens = secretTokens;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Creates the Admin with its set-password link.
     *
     * <p>Built in M6.
     *
     * @param email the new Admin's address, read at a prompt that does not echo it
     * @param call the step's server context
     * @return the new account, its link's email, and the notice every active Admin gets
     * @throws RuleViolation with Access's {@code EmailInUse} when an account holds the address
     *     (BR-45)
     * @throws CompanyNotSetUp when no Admin has set up the company profile yet, so there is no
     *     default language (FR-182)
     */
    public OpenedAccount apply(EmailAddress email, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
