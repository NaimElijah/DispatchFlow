package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.StepUp;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.shared.application.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access's implementation of {@link StepUp}: it loads the actor's session and account, and asks
 * {@code Session.requireFreshStepUp} with the account's current generation and the server's time
 * (NFR-214, ADR-068, ADR-104).
 *
 * <p>Built in M6.
 */
@Transactional(readOnly = true)
public class StepUpService implements StepUp {

    private final SessionRepository sessions;
    private final UserAccountRepository accounts;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param sessions where sessions are kept
     * @param accounts where accounts are kept, for the current generation
     * @param clock the server's time
     */
    public StepUpService(SessionRepository sessions, UserAccountRepository accounts, Clock clock) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M6.
     */
    @Override
    public void requireFresh(Actor actor) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
