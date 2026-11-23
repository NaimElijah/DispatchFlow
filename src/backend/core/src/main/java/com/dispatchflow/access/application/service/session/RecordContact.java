package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the time of a request from the acting session, which BR-133's contact window reads when
 * Live Operations judges a late report (BR-132, NFR-280). The endpoint calls it for every request
 * of an account with the Driver role other than a token refresh or an outbox send, which it decides
 * itself, never from a header the client sets (BR-133).
 *
 * <p>{@code Session.recordContact} keeps at most one time a minute and moves no version (DL §4.1).
 * It writes no audit record: NFR-23 audits the request's own operation.
 *
 * <p>Built in M8.
 */
@Transactional
public class RecordContact {

    private final SessionRepository sessions;
    private final Clock clock;

    /**
     * Creates the use case.
     *
     * @param sessions where sessions are kept
     * @param clock the time
     */
    public RecordContact(SessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    /**
     * Records the contact.
     *
     * <p>Built in M8.
     *
     * @param actor the signed-in user acting
     * @throws RuleViolation with Access's {@code SessionRevoked} when the session is revoked
     * @throws NotFound when the actor's session no longer exists
     */
    public void record(Actor actor) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
