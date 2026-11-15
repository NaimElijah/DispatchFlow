package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * The server step run when the system is deployed creates the first Admin, only while no active
 * Admin exists, with a set-password link and no default credential (FR-01, FR-291). There is no
 * public sign-up.
 *
 * <p>The step reads the address at a prompt that does not echo it, so it never enters a command
 * line or shell history (FR-393), and its audit record holds the account ID only (FR-409). It
 * creates no company profile: the Admin's first save of the settings does, through {@code
 * ChangeCompanyProfile}. So it takes the first Admin's language as given, where every later account
 * starts in the profile's default (FR-182).
 *
 * <p>Built in M6.
 */
public final class CreateFirstAdmin {

    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final EmailSender emailSender;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the step.
     *
     * @param accounts where accounts are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param emailSender sends links and notices by email
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public CreateFirstAdmin(
            UserAccountRepository accounts,
            SecretTokens secretTokens,
            EmailSender emailSender,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.emailSender = emailSender;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Creates the first Admin and sends the set-password link.
     *
     * <p>Built in M6.
     *
     * @param email the first Admin's address, read at a prompt that does not echo it
     * @param language the first Admin's language
     * @param call the step's server context
     * @return the new account's ID
     * @throws RuleViolation with Access's {@code ActiveAdminExists} when an active Admin exists
     *     (FR-291); {@code EmailInUse} when an account holds the address (BR-45)
     */
    public AccountId create(EmailAddress email, Language language, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
