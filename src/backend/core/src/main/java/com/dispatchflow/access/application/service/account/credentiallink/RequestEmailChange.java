package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.GrantLimitExceeded;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * Asks to change an account's email: a link goes to the new address, which takes effect only when
 * the link is used, within 1 hour (FR-387, FR-388). The request is answered the same whether or not
 * another account holds the address, which is refused only then (FR-392).
 *
 * <p>An Admin can change a user's email, except another Admin's, which only that Admin can change
 * (FR-387, ADR-116); a Dispatcher or a Driver can change only their own (the permission matrix).
 * Only an Admin may act on another account, and then only after a fresh step-up: the acting session
 * must have given its password in the last 5 minutes (NFR-214, {@code Capability.requiresStepUp}).
 * An actor whose roles give only the limited grant is refused with {@link GrantLimitExceeded} when
 * the account isn't their own, before the account is read, so the 403 confirms no ID. On the
 * actor's own account it needs the current password instead, which it checks through {@code
 * PasswordHasher.matches} (FR-387); a wrong one counts toward the sign-in limits, in the rate-limit
 * adapter (FR-177, ADR-071). Access's domain has no error for a missing or wrong password yet:
 * {@code SignIn} adds the wrong-password one, and the email links the missing one. Like every
 * changing call it writes its audit record, a refused attempt's in its own transaction (NFR-23,
 * ADR-110).
 *
 * <p>The web adapter limits requests for an email-change link to 5 an hour per account that
 * requests them, keyed by the signed-in actor, so no one can spend another account's limit. It
 * counts them with Bucket4j, and answers a request over the limit 429 before it reaches this use
 * case (FR-411, ADR-071, ADR-121).
 *
 * <p><b>The transaction.</b> {@link RequestEmailChangeChange} holds it: the grant, step-up and
 * password checks, the link's issue, its save and the audit record. After its commit, the use case
 * sends the link to the new address, so a request that rolls back sends nothing (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class RequestEmailChange {

    private final EmailSender emailSender;
    private final RequestEmailChangeChange change;

    /**
     * Creates the use case.
     *
     * @param emailSender sends links and notices by email
     * @param change the transaction that issues the email-change link
     */
    public RequestEmailChange(EmailSender emailSender, RequestEmailChangeChange change) {
        this.emailSender = emailSender;
        this.change = change;
    }

    /**
     * Sends the email-change link to the new address.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param account the account whose email changes
     * @param newEmail the new address
     * @param currentPassword the actor's current password, as typed: required on their own account,
     *     and empty on another's
     * @param expected the version of the account the caller last read (BR-76)
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code EmailOfAnotherAdmin} when the account holds the Admin role
     *     and the actor is another account (FR-387); on the actor's own account, the refusal
     *     the email links add when the current password is missing, or the one {@code SignIn} adds
     *     when it is wrong (FR-387);
     *     on another account, {@code StepUpRequired} when the acting session gave its password more
     *     than 5 minutes ago, or {@code SessionRevoked} when it is revoked (NFR-214)
     * @throws GrantLimitExceeded when the actor's roles give only the limited grant, a Dispatcher's
     *     or a Driver's "only their own", and {@code account} isn't the actor's own; checked before
     *     the account is read, so it answers alike for an ID no account has (the permission matrix)
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void request(
            Actor actor,
            AccountId account,
            EmailAddress newEmail,
            Optional<PresentedPassword> currentPassword,
            Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
