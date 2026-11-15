package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.TokenSigner;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.Clock;

/**
 * The server step a restore runs before the core serves again (NFR-252, NFR-253, NFR-272, ADR-045):
 * it revokes every session, rotates the access-token signing key, and removes the old key from the
 * accepted set at once, with no overlap. A restored database holds its snapshot's generations, so
 * without this a token issued or revoked after the snapshot could read as valid.
 *
 * <p>It revokes each session {@code SessionRepository.allLive} names, in its own transaction, and
 * rotates the key through {@code TokenSigner.rotateKey}. The restore runbook's other steps re-apply
 * the changes NFR-253 names and send the Admins their set-password links.
 *
 * <p>Built in M13.
 */
public final class SecureAfterRestore {

    private final SessionRepository sessions;
    private final TokenSigner tokenSigner;
    private final RevocationCache revocationCache;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the step.
     *
     * @param sessions where sessions are kept
     * @param tokenSigner signs access tokens
     * @param revocationCache the token check's cache, evicted after a change
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SecureAfterRestore(
            SessionRepository sessions,
            TokenSigner tokenSigner,
            RevocationCache revocationCache,
            Clock clock,
            AuditLog auditLog) {
        this.sessions = sessions;
        this.tokenSigner = tokenSigner;
        this.revocationCache = revocationCache;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Revokes every session and rotates the signing key.
     *
     * <p>Built in M13.
     *
     * @param call the step's server context
     */
    public void secure(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
