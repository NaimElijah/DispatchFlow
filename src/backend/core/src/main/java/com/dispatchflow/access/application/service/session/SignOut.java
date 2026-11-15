package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.PushTokens;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Signs the acting session out, which revokes it (NFR-89, FR-127): its tokens are refused from then
 * on, and its open streams close at the next heartbeat (NFR-267). It removes the session's push
 * address (NFR-263), evicts it from the revocation cache (ADR-045), and writes the audit record
 * every sign-out leaves (NFR-23). Signing out again changes nothing.
 *
 * <p>Built in M5.
 */
public final class SignOut {

    private final SessionRepository sessions;
    private final PushTokens pushTokens;
    private final RevocationCache revocationCache;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param sessions where sessions are kept
     * @param pushTokens the push addresses of signed-in phones
     * @param revocationCache the token check's cache, evicted after a change
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SignOut(
            SessionRepository sessions,
            PushTokens pushTokens,
            RevocationCache revocationCache,
            Clock clock,
            AuditLog auditLog) {
        this.sessions = sessions;
        this.pushTokens = pushTokens;
        this.revocationCache = revocationCache;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Signs the acting session out.
     *
     * <p>Built in M5.
     *
     * @param actor the signed-in user acting
     * @throws NotFound when the actor's session no longer exists
     * @throws StaleVersion when the session changed between its read and its save (BR-76)
     */
    public void signOut(Actor actor) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
