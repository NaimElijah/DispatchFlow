package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.PasswordHasher;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.TokenSigner;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * The acting session gives the current password before an Admin-only operation, which allows those
 * operations for 5 minutes (NFR-214). {@code Session.confirmPassword} moves the session's
 * generation, so its older tokens are refused, and it gets new ones (NFR-227). The use case evicts
 * the session from the revocation cache after its commit (ADR-045).
 *
 * <p>A wrong password counts toward the sign-in limits, in the rate-limit adapter (FR-177,
 * ADR-071). Access's domain has no error for a wrong password yet; M5 adds it, with {@code SignIn}.
 * Like every changing call it writes its audit record, a refused attempt's in its own transaction
 * (NFR-23, ADR-110).
 *
 * <p>Built in M6.
 */
public final class ConfirmPassword {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SecretTokens secretTokens;
    private final TokenSigner tokenSigner;
    private final IdGenerator ids;
    private final RevocationCache revocationCache;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     * @param passwordHasher hashes passwords and checks a presented one
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param tokenSigner signs access tokens
     * @param ids makes new IDs
     * @param revocationCache the token check's cache, evicted after a change
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ConfirmPassword(
            UserAccountRepository accounts,
            SessionRepository sessions,
            PasswordHasher passwordHasher,
            SecretTokens secretTokens,
            TokenSigner tokenSigner,
            IdGenerator ids,
            RevocationCache revocationCache,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.secretTokens = secretTokens;
        this.tokenSigner = tokenSigner;
        this.ids = ids;
        this.revocationCache = revocationCache;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Confirms the password.
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
     * @throws StaleVersion when the session changed between its read and its save (BR-76); the web
     *     adapter retries the call at once, up to three times, then answers 503 with {@code
     *     Retry-After} (ADR-121)
     */
    public SignedIn confirm(Actor actor, PresentedPassword password) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
