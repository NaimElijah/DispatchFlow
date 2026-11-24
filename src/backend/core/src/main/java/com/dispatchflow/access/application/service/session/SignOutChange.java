package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.session.PushTokens;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.application.Clock;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link SignOut} (ADR-129): it finds the session by the token's hash, revokes it
 * through {@code Session.revoke}, stores it through {@code SessionRepository.saveRevocation},
 * removes its push address (NFR-263), and writes the audit record every sign-out leaves (NFR-23).
 * The use case evicts the session from the revocation cache after the commit.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class SignOutChange {

    private final SessionRepository sessions;
    private final SecretTokens secretTokens;
    private final PushTokens pushTokens;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param sessions where sessions are kept
     * @param secretTokens hashes the presented refresh token
     * @param pushTokens the push addresses of signed-in phones
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SignOutChange(
            SessionRepository sessions,
            SecretTokens secretTokens,
            PushTokens pushTokens,
            Clock clock,
            AuditLog auditLog) {
        this.sessions = sessions;
        this.secretTokens = secretTokens;
        this.pushTokens = pushTokens;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Revokes the session that keeps the token, and stores it.
     *
     * <p>To Be Implemented.
     *
     * @param refreshToken the refresh token the device presented
     * @param call the call's context: the client and the trace, and no user or session, since the
     *     call carries no access token (ADR-110)
     * @return the session signed out, or empty when no chain keeps the token
     */
    public Optional<SessionId> apply(PresentedToken refreshToken, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
