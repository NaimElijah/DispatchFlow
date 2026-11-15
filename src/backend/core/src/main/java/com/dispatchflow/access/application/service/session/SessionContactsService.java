package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.SessionContacts;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.shared.AccountId;
import java.time.Instant;
import java.util.List;

/**
 * Access's implementation of {@code SessionContacts}: the contact times of an account's sessions
 * within a span, which Live Operations' {@code HoldCheck} reads (BR-132, BR-133, ADR-107, placement
 * P8).
 *
 * <p>It reads {@code SessionRepository.contactsOf}, which covers every session of the account,
 * revoked ones included.
 *
 * <p>Built in M8.
 */
public final class SessionContactsService implements SessionContacts {

    private final SessionRepository sessions;

    /**
     * Creates the service.
     *
     * @param sessions where sessions are kept
     */
    public SessionContactsService(SessionRepository sessions) {
        this.sessions = sessions;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M8.
     */
    @Override
    public List<Instant> contactsOf(AccountId account, Instant from, Instant to) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
