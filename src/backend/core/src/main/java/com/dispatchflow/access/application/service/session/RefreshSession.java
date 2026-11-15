package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.PushTokens;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.TokenSigner;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * Exchanges a refresh token for new tokens through {@code Session.exchange} (NFR-86 to NFR-88,
 * NFR-178): the current token under 14 days old, or one exchanged in the last 30 seconds, gets new
 * ones; a reused token revokes its session. The session is saved whether the exchange was granted
 * or refused, because a refusal may have revoked it (NFR-88).
 *
 * <p>Every refusal looks the same (NFR-92): an unknown token, one the chain no longer keeps, an
 * expired, superseded or reused one, a revoked session, and a session begun before its account's
 * last revocation (NFR-177). Every refresh leaves an audit record (NFR-23).
 *
 * <p><b>A refusal that revoked the session</b> (NFR-88). When the exchange moved the session from
 * live to revoked, the use case writes the revocation through {@code
 * SessionRepository.writeRevocation}, which takes no version, so no conflict can lose it. After the
 * commit it evicts the session from the revocation cache, in the same thread, so the next request
 * sees it (ADR-045), and removes the session's push address through {@code PushTokens.removeFor}
 * (NFR-263).
 *
 * <p><b>A version conflict.</b> A grant, or a refusal that revoked nothing, is saved with the
 * version check. Two refreshes of one session can race, as when a device retries on weak signal.
 * When the save meets a {@code StaleVersion}, the use case reloads the session and exchanges the
 * presented token once more. When the other change exchanged the same token moments ago, the second
 * exchange lands in NFR-178's 30-second grace and gets the token that replaces the one the other
 * exchange issued, revoking nothing. A second exchange that revokes is written as above. A second
 * conflict answers the one refusal, unsaved: only a grant or housekeeping is lost then, never a
 * revocation. So no conflict leaves this use case.
 *
 * <p>The removal through {@code PushTokens} joins in M10, which builds that port; until then no
 * session has a push address.
 *
 * <p>Built in M6.
 */
public final class RefreshSession {

    private final SessionRepository sessions;
    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final TokenSigner tokenSigner;
    private final RevocationCache revocationCache;
    private final PushTokens pushTokens;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param sessions where sessions are kept
     * @param accounts where accounts are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param tokenSigner signs access tokens
     * @param revocationCache the token check's cache, evicted after a revocation
     * @param pushTokens the push addresses of signed-in phones, a revoked session's removed
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RefreshSession(
            SessionRepository sessions,
            UserAccountRepository accounts,
            SecretTokens secretTokens,
            TokenSigner tokenSigner,
            RevocationCache revocationCache,
            PushTokens pushTokens,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.tokenSigner = tokenSigner;
        this.revocationCache = revocationCache;
        this.pushTokens = pushTokens;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Exchanges the token.
     *
     * <p>Built in M6.
     *
     * @param token the refresh token the device presented
     * @param call the call's context: the client and the trace, and no user yet (ADR-110)
     * @return new tokens, or the one refusal
     */
    public RefreshAnswer refresh(PresentedToken token, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
