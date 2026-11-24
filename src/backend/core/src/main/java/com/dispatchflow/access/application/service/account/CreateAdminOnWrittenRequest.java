package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.CompanyNotSetUp;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;

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
 * <p><b>The transaction.</b> {@link CreateAdminOnWrittenRequestChange} holds it: the new account
 * with its link, the Admins' addresses and the audit record. After its commit, the step sends the
 * set-password link and the Admins' notice, so a creation that rolls back sends nothing (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class CreateAdminOnWrittenRequest {

    private final EmailSender emailSender;
    private final CreateAdminOnWrittenRequestChange change;

    /**
     * Creates the step.
     *
     * @param emailSender sends links and notices by email
     * @param change the transaction that creates the Admin
     */
    public CreateAdminOnWrittenRequest(EmailSender emailSender, CreateAdminOnWrittenRequestChange change) {
        this.emailSender = emailSender;
        this.change = change;
    }

    /**
     * Creates the Admin and sends the set-password link.
     *
     * <p>To Be Implemented.
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
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
