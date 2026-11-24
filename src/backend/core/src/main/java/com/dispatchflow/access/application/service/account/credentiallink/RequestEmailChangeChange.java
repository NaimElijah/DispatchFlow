package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.GrantLimitExceeded;
import com.dispatchflow.access.application.port.out.PasswordHasher;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.application.service.account.LinkEmail;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RequestEmailChange} (ADR-129): it checks the actor's grant before it
 * reads the account, then the step-up on another account or the current password on the actor's
 * own, issues the email-change link through the account, saves it, and writes the audit record
 * (NFR-23). The use case sends the link after the commit.
 *
 * <p>Built in M6.
 */
@Transactional
public class RequestEmailChangeChange {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SecretTokens secretTokens;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     * @param passwordHasher hashes passwords and checks a presented one
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RequestEmailChangeChange(
            UserAccountRepository accounts,
            SessionRepository sessions,
            PasswordHasher passwordHasher,
            SecretTokens secretTokens,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.secretTokens = secretTokens;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Issues the email-change link, and saves the account.
     *
     * <p>Built in M6.
     *
     * @param actor the signed-in user acting
     * @param account the account whose email changes
     * @param newEmail the new address
     * @param currentPassword the actor's current password, as typed: required on their own account,
     *     and empty on another's
     * @param expected the version of the account the caller last read (BR-76)
     * @return the link's email, to the new address (FR-388)
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code EmailOfAnotherAdmin} when the account holds the Admin role
     *     and the actor is another account (FR-387); on the actor's own account, the refusal M6
     *     adds when the current password is missing, or the one M5 adds when it is wrong (FR-387);
     *     on another account, {@code StepUpRequired} when the acting session gave its password more
     *     than 5 minutes ago, or {@code SessionRevoked} when it is revoked (NFR-214)
     * @throws GrantLimitExceeded when the actor's roles give only the limited grant and {@code
     *     account} isn't the actor's own; checked before the account is read
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public LinkEmail apply(
            Actor actor,
            AccountId account,
            EmailAddress newEmail,
            Optional<PresentedPassword> currentPassword,
            Version expected) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
