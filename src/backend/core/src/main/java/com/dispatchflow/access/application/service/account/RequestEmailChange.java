package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.GrantLimitExceeded;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.PasswordHasher;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
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
 * adapter (FR-177, ADR-071). Access's domain has no error for a missing or wrong password yet; M6
 * adds it. Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M6.
 */
public final class RequestEmailChange {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
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
     * @param passwordHasher hashes passwords and checks a presented one
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param emailSender sends links and notices by email
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RequestEmailChange(
            UserAccountRepository accounts,
            SessionRepository sessions,
            PasswordHasher passwordHasher,
            SecretTokens secretTokens,
            EmailSender emailSender,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.secretTokens = secretTokens;
        this.emailSender = emailSender;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Sends the email-change link to the new address.
     *
     * <p>Built in M6.
     *
     * @param actor the signed-in user acting
     * @param account the account whose email changes
     * @param newEmail the new address
     * @param currentPassword the actor's current password, as typed: required on their own account,
     *     and empty on another's
     * @param expected the version of the account the caller last read (BR-76)
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code EmailOfAnotherAdmin} when the account holds the Admin role
     *     and the actor is another account (FR-387); on the actor's own account, the wrong-password
     *     refusal M6 adds when the current password is missing or wrong (FR-387); on another
     *     account, {@code StepUpRequired} when the acting session gave its password more than 5
     *     minutes ago, or {@code SessionRevoked} when it is revoked (NFR-214)
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
        throw new UnsupportedOperationException("Built in M6");
    }
}
