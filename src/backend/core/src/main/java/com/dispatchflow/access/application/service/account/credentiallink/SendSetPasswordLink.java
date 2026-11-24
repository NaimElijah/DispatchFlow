package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * An Admin sends a user who has no password yet a new set-password link, which voids the older one
 * (FR-294, NFR-224). It works once and for 72 hours, and allows nothing but setting a password. A
 * user with a password recovers it instead (FR-345).
 *
 * <p>The operation is Admin-only in the permission matrix, so the acting session must have given
 * its password in the last 5 minutes, which {@code Session.requireFreshStepUp} checks (NFR-214,
 * NFR-227). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p><b>The transaction.</b> {@link SendSetPasswordLinkChange} holds it: the step-up check, the
 * link's issue, its save and the audit record. After its commit, the use case sends the link, so a
 * change that rolls back sends nothing (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class SendSetPasswordLink {

    private final EmailSender emailSender;
    private final SendSetPasswordLinkChange change;

    /**
     * Creates the use case.
     *
     * @param emailSender sends links and notices by email
     * @param change the transaction that issues the link
     */
    public SendSetPasswordLink(EmailSender emailSender, SendSetPasswordLinkChange change) {
        this.emailSender = emailSender;
        this.change = change;
    }

    /**
     * Sends the link.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param account the user's account
     * @param expected the version of the account the caller last read (BR-76)
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code PasswordAlreadySet} when it has a password (FR-294,
     *     FR-345); {@code StepUpRequired} when the acting session gave its password more than 5
     *     minutes ago, or {@code SessionRevoked} when it is revoked (NFR-214)
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void send(Actor actor, AccountId account, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
