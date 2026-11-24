package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.application.service.account.LinkEmail;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link SendSetPasswordLink} (ADR-129): it checks the acting session's step-up,
 * issues the set-password link through the account, saves it, and writes the audit record
 * (NFR-23). The use case sends the link after the commit.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class SendSetPasswordLinkChange {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final SecretTokens secretTokens;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SendSetPasswordLinkChange(
            UserAccountRepository accounts,
            SessionRepository sessions,
            SecretTokens secretTokens,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.secretTokens = secretTokens;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Issues the link, and saves the account.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param account the user's account
     * @param expected the version of the account the caller last read (BR-76)
     * @return the link's email, to the address stored on the account (BR-125)
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code PasswordAlreadySet} when it has a password (FR-294,
     *     FR-345); {@code StepUpRequired} when the acting session gave its password more than 5
     *     minutes ago, or {@code SessionRevoked} when it is revoked (NFR-214)
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public LinkEmail apply(Actor actor, AccountId account, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
