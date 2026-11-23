package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.SessionValidity;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access's implementation of {@code SessionValidity}: whether an access token's account and session
 * generations are still current, for the security filter and the stream's heartbeat (ADR-045,
 * NFR-177, NFR-267). It reads through two narrow reads, never a whole aggregate: {@code
 * UserAccountRepository.activeGenerationOf} and {@code SessionRepository.liveGenerationOf}. The
 * persistence adapter caches those reads (ADR-045), so this stays the one implementation of {@code
 * SessionValidity}, cached or not.
 *
 * <p>Built in M6.
 */
@Transactional(readOnly = true)
public class SessionValidityService implements SessionValidity {

    private final UserAccountRepository accounts;
    private final SessionRepository sessions;

    /**
     * Creates the service.
     *
     * @param accounts where accounts are kept
     * @param sessions where sessions are kept
     */
    public SessionValidityService(UserAccountRepository accounts, SessionRepository sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M6.
     */
    @Override
    public boolean isCurrent(AccountId account, long accountGeneration, SessionId session, long sessionGeneration) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
