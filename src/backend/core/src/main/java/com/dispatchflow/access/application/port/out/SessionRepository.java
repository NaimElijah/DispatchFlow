package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.session.Session;
import com.dispatchflow.access.domain.session.SessionGeneration;
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
 * <p>Built in M5, M6, M8 and M13, method by method.
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
     * A session's state and generation in one answer, which the token check compares with a token's
     * {@code sgen} on every request and the stream's heartbeat reads (ADR-045, NFR-177, NFR-267).
     * It reads no aggregate. The adapter answers it through the revocation cache's atomic loader,
     * so the cache sits in the adapter and {@code SessionValidity} keeps one implementation; {@link
     * RevocationCache#evict(SessionId)} drops the entry after a change.
     *
     * <p>Built in M6.
     *
     * @param session the token's session
     * @return the session's generation while it is live, or empty when no session has the ID or it
     *     is revoked
     */
    Optional<SessionGeneration> liveGenerationOf(SessionId session);

    /**
     * The session whose chain holds a refresh token with the hash, current or not, which {@code
     * Session.exchange} judges (NFR-88, NFR-178), and which a sign-out revokes (NFR-89).
     *
     * <p>Built in M5.
     *
     * @param token the hash of the presented refresh token
     * @return the session, or empty when no chain keeps the token
     */
    Optional<Session> withRefreshToken(TokenHash token);

    /**
     * The sessions an account's revocation ended, which the listener on {@code SessionsRevoked}
     * dates through {@code Session.revokeWithAccount} (NFR-90, NFR-258, ADR-116): those begun under
     * a generation older than {@code revokedTo} that are live, or that were revoked after {@code
     * revokedAt}, as a session signed out after its account's revocation but before the listener
     * ran. A session revoked earlier, or begun under {@code revokedTo} or later, is left out, since
     * {@code revokeWithAccount} would change nothing on it.
     *
     * <p>Built in M6.
     *
     * @param account the account
     * @param revokedTo the account's generation after the revocation, which the event carries
     * @param revokedAt when the account's sessions were revoked
     * @return those sessions, possibly none
     */
    List<Session> endedByRevocation(AccountId account, AccountGeneration revokedTo, Instant revokedAt);

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
     * Stores a session the domain revoked, with no version check, so a version conflict never loses
     * the revocation: the one {@code Session.exchange} makes on a reused token (NFR-88), and a
     * sign-out's through {@code Session.revoke} (NFR-89). The domain decides the revocation and its
     * instant; the adapter applies no rule of its own. It writes the session's revoked state only
     * over a stored row that is still live, so a revocation already stored is kept, and moves the
     * version, so a concurrent save of a copy read before fails its own check rather than writing
     * the session live again. It writes nothing else: the rest of a refused exchange, the old
     * tokens it dropped, is housekeeping a revoked session never needs.
     *
     * <p>No version check is needed because a session only ever moves from live to revoked, and
     * nothing un-revokes one, so the write can't overwrite a change it should have seen. A retry of
     * the whole exchange could meet a conflict each time, and lose the revocation in the end.
     *
     * <p>Built in M5, for sign-out; refresh uses it from M6.
     *
     * @param session the session, revoked by {@code Session.revoke} or {@code Session.exchange}
     * @throws IllegalArgumentException when the session is live: the use case saves a live session
     *     through {@link #save}, so this is a programming error rather than a refusal
     */
    void saveRevocation(Session session);

    /**
     * Deletes every session that ended before an instant, by {@code Session.endedAt}: sessions are
     * deleted 30 days after they end (NFR-258). A session that ended by its token's expiry was
     * never revoked, so it may still hold a push address, which the caller removes by the IDs
     * answered.
     *
     * <p>Built in M13.
     *
     * @param cutoff the latest end that is due
     * @return the IDs of the sessions deleted, each once, in any order
     */
    List<SessionId> deleteEndedBefore(Instant cutoff);

    /**
     * The sessions holding a contact time older than an instant, whose old times the retention job
     * drops through {@code Session.dropOldContacts} (NFR-280).
     *
     * <p>Built in M13.
     *
     * @param cutoff the oldest contact time kept
     * @return their IDs, in any order
     */
    List<SessionId> withContactsBefore(Instant cutoff);
}
