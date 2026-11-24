package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link CreateFirstAdmin} (ADR-129): it checks that no active Admin exists,
 * creates the first Admin's account with its set-password link, adds it, and writes the audit
 * record, which holds the account ID only (FR-409). The step sends the link after the commit.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class CreateFirstAdminChange {

    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public CreateFirstAdminChange(
            UserAccountRepository accounts,
            SecretTokens secretTokens,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Creates the first Admin with its set-password link.
     *
     * <p>To Be Implemented.
     *
     * @param email the first Admin's address, read at a prompt that does not echo it
     * @param language the first Admin's language
     * @param call the step's server context
     * @return the new account and its link's email, with no Admins' notice, since no other Admin is
     *     active (FR-291)
     * @throws RuleViolation with Access's {@code ActiveAdminExists} when an active Admin exists
     *     (FR-291); {@code EmailInUse} when an account holds the address (BR-45)
     */
    public OpenedAccount apply(EmailAddress email, Language language, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
