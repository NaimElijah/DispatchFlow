package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.application.port.out.session.PushTokens;
import com.dispatchflow.access.application.port.out.session.TokenSigner;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * One exchange's transaction in {@link RefreshSession} (ADR-129): it finds the session by the
 * token's hash, exchanges the token through {@code Session.exchange}, saves the session, through
 * {@code SessionRepository.saveRevocation} when the exchange revoked it, removes a revoked session's
 * push address through {@code PushTokens.removeFor} (NFR-263), and writes the audit record (NFR-23).
 * The use case evicts a revoked session from the revocation cache after the commit.
 *
 * <p>Built in M6.
 */
@Transactional
public class RefreshSessionChange {

    private final SessionRepository sessions;
    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final TokenSigner tokenSigner;
    private final PushTokens pushTokens;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param sessions where sessions are kept
     * @param accounts where accounts are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param tokenSigner signs access tokens
     * @param pushTokens the push addresses of signed-in phones, a revoked session's removed
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RefreshSessionChange(
            SessionRepository sessions,
            UserAccountRepository accounts,
            SecretTokens secretTokens,
            TokenSigner tokenSigner,
            PushTokens pushTokens,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.tokenSigner = tokenSigner;
        this.pushTokens = pushTokens;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Exchanges the token once, and saves the session.
     *
     * <p>Built in M6.
     *
     * @param token the refresh token the device presented
     * @param call the call's context: the client and the trace, and no user yet (ADR-110)
     * @return the answer, and the session the exchange revoked, if it revoked one
     * @throws StaleVersion when a grant, or a refusal that revoked nothing, meets a session that
     *     changed between its read and its save (BR-76); the use case exchanges once more
     */
    public Exchanged apply(PresentedToken token, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }

    /**
     * What one exchange answers, and what it leaves the use case to evict after the commit. The
     * answer may hold tokens, which are secrets (NFR-19), so it prints redacted.
     *
     * <p>Built in M6.
     *
     * @param answer new tokens, or the one refusal
     * @param revoked the session the exchange revoked, or empty when it revoked none
     */
    public record Exchanged(RefreshAnswer answer, Optional<SessionId> revoked) implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public Exchanged {
            Objects.requireNonNull(answer, "answer");
            Objects.requireNonNull(revoked, "revoked");
        }

        /**
         * A form that hides the tokens.
         *
         * @return {@code RefreshSessionChange.Exchanged[redacted]}
         */
        @Override
        public String toString() {
            return "RefreshSessionChange.Exchanged[redacted]";
        }
    }
}
