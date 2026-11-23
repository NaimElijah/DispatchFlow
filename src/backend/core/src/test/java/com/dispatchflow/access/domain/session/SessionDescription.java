package com.dispatchflow.access.domain.session;

import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import java.time.Instant;
import java.util.List;

/**
 * What a test sees of a {@link Session}, read through its getters: a value that compares by every
 * part. Tests compare a session before and after a refused command, or after a load with the
 * session that was added, through it. It leaves the version out, which tests check on its own.
 *
 * @param id the session's ID
 * @param account the account it signed in
 * @param accountGeneration the account's generation when it began
 * @param generation its own generation
 * @param state live or revoked
 * @param openedAt when it signed in
 * @param lastPasswordAt when it last gave the password
 * @param contactTimes its contact times, oldest first
 * @param tokens its refresh tokens, in the order they were issued
 */
public record SessionDescription(
        SessionId id,
        AccountId account,
        AccountGeneration accountGeneration,
        SessionGeneration generation,
        SessionState state,
        Instant openedAt,
        Instant lastPasswordAt,
        List<Instant> contactTimes,
        List<TokenDescription> tokens) {

    /**
     * Describes a session.
     *
     * @param session the session
     * @return what its getters give
     */
    public static SessionDescription of(Session session) {
        return new SessionDescription(
                session.id(),
                session.account(),
                session.accountGeneration(),
                session.generation(),
                session.state(),
                session.openedAt(),
                session.lastPasswordAt(),
                session.contactTimes(),
                TokenDescription.everyTokenOf(session));
    }
}
