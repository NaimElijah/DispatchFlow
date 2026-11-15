package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.PushTokens;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Signs a session out by its refresh token, which revokes it (NFR-89, FR-127): its tokens are
 * refused from then on, and its open streams close at the next heartbeat (NFR-267). Sign-out needs
 * no access token (NFR-01, ADR-069, ADR-071), so a device whose access token has expired can still
 * end its session.
 *
 * <p>The use case hashes the presented token through {@code SecretTokens.hashOf} and finds the
 * session through {@code SessionRepository.withRefreshToken}, which answers for any token the chain
 * keeps, current or not. It revokes that session through {@code Session.revoke}, whatever the
 * token's place in the chain. It removes the session's push address (NFR-263), evicts the session
 * from the revocation cache after its commit (ADR-045), and writes the audit record every sign-out
 * leaves (NFR-23), with the call's context completed by the account and the session it found.
 *
 * <p>Signing out again changes nothing. A token no chain keeps gets the same answer, and its audit
 * record names no user or session, so the caller learns nothing about the token.
 *
 * <p>The endpoint's CSRF check on the web app's cookie is the web adapter's, as it is for refresh
 * (NFR-107, ADR-069); this use case sees only the token.
 *
 * <p>In M5 it finds and revokes the session and writes the audit record. The eviction from {@code
 * RevocationCache} joins in M6, and the removal through {@code PushTokens} in M10, the milestones
 * that build those ports.
 *
 * <p>Built in M5.
 */
public final class SignOut {

    private final SessionRepository sessions;
    private final SecretTokens secretTokens;
    private final PushTokens pushTokens;
    private final RevocationCache revocationCache;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param sessions where sessions are kept
     * @param secretTokens hashes the presented refresh token
     * @param pushTokens the push addresses of signed-in phones
     * @param revocationCache the token check's cache, evicted after a change
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SignOut(
            SessionRepository sessions,
            SecretTokens secretTokens,
            PushTokens pushTokens,
            RevocationCache revocationCache,
            Clock clock,
            AuditLog auditLog) {
        this.sessions = sessions;
        this.secretTokens = secretTokens;
        this.pushTokens = pushTokens;
        this.revocationCache = revocationCache;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Signs the session that keeps the token out.
     *
     * <p>Built in M5.
     *
     * @param refreshToken the refresh token the device presented
     * @param call the call's context: the client and the trace, and no user or session, since the
     *     call carries no access token (ADR-110)
     * @throws StaleVersion when the session changed between its read and its save (BR-76)
     */
    public void signOut(PresentedToken refreshToken, CallContext call) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
