package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.session.Session;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where sessions are kept, whole, with their contact times and refresh-token chains (DL §3.6,
 * §4.1).
 *
 * <p>Built in M5.
 */
public interface SessionRepository {

    /**
     * The session with an ID.
     *
     * <p>Built in M5.
     *
     * @param id the session's ID
     * @return the session, or empty when no session has the ID
     */
    Optional<Session> get(SessionId id);

    /**
     * The session whose chain holds a refresh token with the hash, current or not, which {@code
     * Session.exchange} judges (NFR-88, NFR-178).
     *
     * <p>Built in M6.
     *
     * @param token the hash of the presented refresh token
     * @return the session, or empty when no chain keeps the token
     */
    Optional<Session> withRefreshToken(TokenHash token);

    /**
     * The account's live sessions, which the listener on {@code SessionsRevoked} ends (NFR-90,
     * ADR-116).
     *
     * <p>Built in M6.
     *
     * @param account the account
     * @return its live sessions, possibly none
     */
    List<Session> liveOf(AccountId account);

    /**
     * Every live session of every account, which the restore's server step revokes (NFR-252). It
     * answers IDs, so each session is loaded, revoked and saved in its own transaction.
     *
     * <p>Built in M13.
     *
     * @return their IDs, in any order
     */
    List<SessionId> allLive();

    /**
     * The contact times of every session of an account within a span, both ends included, revoked
     * sessions included: a session signed out since its contact still counts (BR-133, NFR-280).
     *
     * <p>Built in M8.
     *
     * @param account the reporting account
     * @param from the first instant of the span
     * @param to the last instant of the span, not before {@code from}
     * @return the contact times in the span, in time order; empty when there was none
     */
    List<Instant> contactsOf(AccountId account, Instant from, Instant to);

    /**
     * Stores a new session (DL §3.6).
     *
     * <p>Built in M5.
     *
     * @param session the new session, at {@code Version.NEW}
     */
    void add(Session session);

    /**
     * Stores a changed session with a version check (DL §3.6, BR-76). A change that only recorded
     * or dropped contact times moves no version, so the adapter writes those times alone, with no
     * version check, and a contact never makes a refresh fail (DL §4.1).
     *
     * <p>Built in M5.
     *
     * @param session the changed session
     * @throws StaleVersion when the stored session is no longer at the version it was loaded at
     */
    void save(Session session);

    /**
     * Deletes every session that ended before an instant, by {@code Session.endedAt}: sessions are
     * deleted 30 days after they end (NFR-258).
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param cutoff the latest end that is due
     * @return how many sessions were deleted
     */
    int deleteEndedBefore(Instant cutoff);

    /**
     * The sessions holding a contact time older than an instant, whose old times the retention job
     * drops through {@code Session.dropOldContacts} (NFR-280).
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param cutoff the oldest contact time kept
     * @return their IDs, in any order
     */
    List<SessionId> withContactsBefore(Instant cutoff);
}
