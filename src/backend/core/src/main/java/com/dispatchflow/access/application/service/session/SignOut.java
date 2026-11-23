package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.audit.application.api.CallContext;

/**
 * Signs a session out by its refresh token, which revokes it (NFR-89, FR-127): its tokens are
 * refused from then on, and its open streams close at the next heartbeat (NFR-267). Sign-out needs
 * no access token (NFR-01, ADR-069, ADR-071), so a device whose access token has expired can still
 * end its session.
 *
 * <p>The use case hashes the presented token through {@code SecretTokens.hashOf} and finds the
 * session through {@code SessionRepository.withRefreshToken}, which answers for any token the chain
 * keeps, current or not. It revokes that session through {@code Session.revoke}, whatever the
 * token's place in the chain, and stores it through {@code SessionRepository.saveRevocation}: a
 * session only ever moves from live to revoked, so the save needs no version check, and no
 * concurrent change can make a sign-out fail. It removes the session's push address (NFR-263),
 * evicts the session from the revocation cache after its commit (ADR-045), and writes the audit
 * record every sign-out leaves (NFR-23), with the call's context completed by the account and the
 * session it found.
 *
 * <p>Signing out again changes nothing. A token no chain keeps gets the same answer, and its audit
 * record names no user or session, so the caller learns nothing about the token.
 *
 * <p>The endpoint's CSRF check on the web app's cookie is the web adapter's, as it is for refresh
 * (NFR-107, ADR-069); this use case sees only the token.
 *
 * <p><b>The transaction.</b> {@link SignOutChange} holds it: the lookup, the revocation and its
 * store, the push address's removal and the audit record. After its commit, the use case evicts the
 * session from the revocation cache (ADR-129).
 *
 * <p>In M5 it finds and revokes the session and writes the audit record. The eviction from {@code
 * RevocationCache} joins in M6, and the removal through {@code PushTokens} in M10, the milestones
 * that build those ports. Until M6 builds {@code SessionValidity}, nothing checks an access token's
 * session, so an access token the signed-out session issued still works until it expires.
 *
 * <p>Built in M5.
 */
public final class SignOut {

    private final RevocationCache revocationCache;
    private final SignOutChange change;

    /**
     * Creates the use case.
     *
     * @param revocationCache the token check's cache, evicted after a change
     * @param change the transaction that revokes the session
     */
    public SignOut(RevocationCache revocationCache, SignOutChange change) {
        this.revocationCache = revocationCache;
        this.change = change;
    }

    /**
     * Signs the session that keeps the token out.
     *
     * <p>Built in M5.
     *
     * @param refreshToken the refresh token the device presented
     * @param call the call's context: the client and the trace, and no user or session, since the
     *     call carries no access token (ADR-110)
     */
    public void signOut(PresentedToken refreshToken, CallContext call) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
