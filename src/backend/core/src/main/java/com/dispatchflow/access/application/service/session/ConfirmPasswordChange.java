package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.PasswordHasher;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.application.port.out.session.TokenSigner;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link ConfirmPassword} (ADR-129): it checks the password, confirms it through
 * {@code Session.confirmPassword}, issues the session's new tokens, saves it, and writes the audit
 * record (NFR-23). The use case evicts the session from the revocation cache after the commit.
 *
 * <p>Built in M6.
 */
@Transactional
public class ConfirmPasswordChange {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SecretTokens secretTokens;
    private final TokenSigner tokenSigner;
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
     * @param tokenSigner signs access tokens
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ConfirmPasswordChange(
            UserAccountRepository accounts,
            SessionRepository sessions,
            PasswordHasher passwordHasher,
            SecretTokens secretTokens,
            TokenSigner tokenSigner,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.secretTokens = secretTokens;
        this.tokenSigner = tokenSigner;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Confirms the password, and saves the session with its new tokens.
     *
     * <p>Built in M6.
     *
     * @param actor the signed-in user acting
     * @param password the current password, as typed
     * @return the session's new tokens
     * @throws RuleViolation with Access's {@code SessionRevoked} when the session is revoked, or
     *     its account's sessions were revoked after it began (NFR-177); or with the wrong-password
     *     refusal M5 adds
     * @throws NotFound when the actor's session or account no longer exists
     * @throws StaleVersion when the session changed between its read and its save (BR-76)
     */
    public SignedIn apply(Actor actor, PresentedPassword password) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
