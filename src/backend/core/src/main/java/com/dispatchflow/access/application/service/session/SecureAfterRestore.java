package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.application.port.out.session.PushTokens;
import com.dispatchflow.access.application.port.out.session.TokenSigner;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * The server step a restore runs before the core serves again (ADR-045): it rotates the
 * access-token signing key and removes the old key from the accepted set at once, with no overlap
 * (NFR-272), revokes every session (NFR-252), and voids every open credential link (NFR-275). A
 * restored database holds its snapshot's generations and links, so without this a token issued or
 * revoked after the snapshot could read as valid, and a link sent before the snapshot could still
 * be used.
 *
 * <p><b>The order.</b> It rotates the key through {@code TokenSigner.rotateKey} first: it is one
 * step, and it refuses every access token signed before the restore, whereas the revocations are
 * one transaction each. So a step that stops part way has already closed the widest gap, and
 * running it again finishes the rest. Then it revokes each session {@code
 * SessionRepository.allLive} names, and voids the links of each account {@code
 * UserAccountRepository.withOpenLinks} names, each in its own transaction.
 *
 * <p>The restore runbook's other steps are not declared yet: re-applying the changes made since the
 * backup (NFR-253), blanking the passwords and voiding the links of the accounts whose password or
 * email changed since it (NFR-274), and issuing the Admins their set-password links (NFR-282). They
 * follow ADR-045 and are built in M13.
 *
 * <p>Domain gap: {@code UserAccount} has no method that voids every open link yet; M13 adds it.
 *
 * <p>Built in M13.
 */
public final class SecureAfterRestore {

    private final SessionRepository sessions;
    private final UserAccountRepository accounts;
    private final TokenSigner tokenSigner;
    private final RevocationCache revocationCache;
    private final PushTokens pushTokens;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the step.
     *
     * @param sessions where sessions are kept
     * @param accounts where accounts are kept, with their credential links
     * @param tokenSigner signs access tokens
     * @param revocationCache the token check's cache, evicted after a change
     * @param pushTokens the phones' push addresses, each revoked session's removed (NFR-263)
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SecureAfterRestore(
            SessionRepository sessions,
            UserAccountRepository accounts,
            TokenSigner tokenSigner,
            RevocationCache revocationCache,
            PushTokens pushTokens,
            Clock clock,
            AuditLog auditLog) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.tokenSigner = tokenSigner;
        this.revocationCache = revocationCache;
        this.pushTokens = pushTokens;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Rotates the signing key, then revokes every session and voids every open credential link.
     *
     * <p>Built in M13.
     *
     * @param call the step's server context
     * @throws StaleVersion when a session or an account changed between its read and its save
     *     (BR-76)
     */
    public void secure(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
