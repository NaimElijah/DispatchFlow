package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.TokenSigner;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.StaleVersion;

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
 * <p>Built in M6.
 */
public final class RefreshSession {

    private final SessionRepository sessions;
    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final TokenSigner tokenSigner;
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
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RefreshSession(
            SessionRepository sessions,
            UserAccountRepository accounts,
            SecretTokens secretTokens,
            TokenSigner tokenSigner,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.tokenSigner = tokenSigner;
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
     * @throws StaleVersion when the session changed between its read and its save (BR-76)
     */
    public RefreshAnswer refresh(PresentedToken token, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
