package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * The written-request step also deactivates an Admin account the request names, and ends its
 * sessions, without the minimum of active Admins (FR-346, an exception to FR-174 and BR-19). Like
 * any deactivation it voids every open link (NFR-224). The change moves the account's generation
 * and raises {@code SessionsRevoked}, so every token issued before is refused at once (NFR-90,
 * NFR-177); the use case evicts the account from the revocation cache after its commit (ADR-045).
 *
 * <p>The step reads the account at a prompt that does not echo it (FR-393), and its audit record
 * holds the account ID only (FR-409).
 *
 * <p>Built in M6.
 */
public final class DeactivateOnWrittenRequest {

    private final UserAccountRepository accounts;
    private final RevocationCache revocationCache;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the step.
     *
     * @param accounts where accounts are kept
     * @param revocationCache the token check's cache, evicted after a change
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public DeactivateOnWrittenRequest(
            UserAccountRepository accounts, RevocationCache revocationCache, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.revocationCache = revocationCache;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Deactivates the Admin account.
     *
     * <p>Built in M6.
     *
     * @param account the Admin account the request names
     * @param call the step's server context
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code NotAnAdmin} when it holds no Admin role
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account changed between its read and its save (BR-76)
     */
    public void deactivate(AccountId account, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
