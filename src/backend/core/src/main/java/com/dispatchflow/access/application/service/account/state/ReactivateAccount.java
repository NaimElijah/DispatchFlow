package com.dispatchflow.access.application.service.account.state;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * An Admin reactivates a deactivated user, whose history stays intact (FR-297); a deactivated Admin
 * counts toward BR-19 again (FR-174). Its sessions stay ended and its links voided: the person
 * signs in again, or recovers. Every active Admin is emailed when it is an Admin account (FR-389):
 * the use case reads them through {@code UserAccountRepository.activeAdminRecipients} and sends
 * {@code EmailSender.sendAdminNotice}.
 *
 * <p>The operation is Admin-only in the permission matrix, so the acting session must have given
 * its password in the last 5 minutes, which {@code Session.requireFreshStepUp} checks (NFR-214,
 * NFR-227). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p><b>The transaction.</b> {@link ReactivateAccountChange} holds it: the step-up check, the
 * reactivation, its save, the Admins' addresses and the audit record. After its commit, the use
 * case sends the Admins' notice, so a reactivation that rolls back sends nothing (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class ReactivateAccount {

    private final EmailSender emailSender;
    private final ReactivateAccountChange change;

    /**
     * Creates the use case.
     *
     * @param emailSender sends links and notices by email
     * @param change the transaction that reactivates the account
     */
    public ReactivateAccount(EmailSender emailSender, ReactivateAccountChange change) {
        this.emailSender = emailSender;
        this.change = change;
    }

    /**
     * Reactivates the account.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param account the user's account
     * @param expected the version of the account the caller last read (BR-76)
     * @throws RuleViolation with Access's {@code AccountNotDeactivated} when it is active; {@code
     *     AccountAnonymized} once anonymized; {@code StepUpRequired} when the acting session gave
     *     its password more than 5 minutes ago, or {@code SessionRevoked} when it is revoked
     *     (NFR-214)
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void reactivate(Actor actor, AccountId account, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
