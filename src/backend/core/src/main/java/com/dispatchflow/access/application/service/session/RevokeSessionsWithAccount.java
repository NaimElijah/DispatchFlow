package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.PushTokens;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.events.SessionsRevoked;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Access's listener on its own {@code SessionsRevoked}: it ends each live session of the account
 * begun under an older generation, through {@code Session.revokeWithAccount}, dated by the
 * revocation, so the retention job deletes it 30 days later (NFR-258, ADR-116). It removes the push
 * address of each session it ends, through {@code PushTokens.removeFor}, and no other: a session
 * begun after the revocation keeps its address (NFR-263).
 *
 * <p>The token check refuses those sessions from the revocation on, whether or not this has run
 * (NFR-177). Delivery is at least once, and a redelivered event changes nothing, so the reaction is
 * idempotent by nature (ADR-064). It writes its audit record with the server step's context,
 * because ADR-110 audits server steps.
 *
 * <p>Built in M6.
 */
public final class RevokeSessionsWithAccount {

    private final SessionRepository sessions;
    private final PushTokens pushTokens;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the listener.
     *
     * @param sessions where sessions are kept
     * @param pushTokens the push addresses of signed-in phones
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RevokeSessionsWithAccount(
            SessionRepository sessions, PushTokens pushTokens, Clock clock, AuditLog auditLog) {
        this.sessions = sessions;
        this.pushTokens = pushTokens;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Ends the account's older sessions.
     *
     * <p>Built in M6.
     *
     * @param event the revocation, with the account's new generation
     * @param call the listener's server step
     * @throws StaleVersion when a session changed between its read and its save (BR-76); the
     *     listener adapter retries at once, up to three times (DL §11)
     */
    public void on(SessionsRevoked event, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
