package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.session.Session;
import com.dispatchflow.shared.AccountId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaSessionRepository}: {@code save}, and the lookups and
 * native statements the port needs. The lookups that find a session answer its key alone, so that
 * {@link #findById} loads it whole.
 */
interface Sessions extends Repository<Session, UUID> {

    /**
     * Saves a session.
     *
     * @param session the session
     * @return the managed session
     */
    Session save(Session session);

    /**
     * Loads a session with its refresh tokens; {@link #withContactTimes} then loads its contact
     * times, so the two collections don't multiply each other's rows.
     *
     * @param id the session's UUID
     * @return the session, or empty
     */
    @EntityGraph(attributePaths = "tokens")
    Optional<Session> findById(UUID id);

    /**
     * Loads a session's contact times into the session this transaction already holds.
     *
     * @param id the session's UUID
     * @return the session, or empty
     */
    @Query("select s from Session s left join fetch s.contactTimes where s.id = :id")
    Optional<Session> withContactTimes(UUID id);

    /**
     * A live session's generation alone, as its stored count. A native query, because Spring Data
     * would read a JPQL query that returns the record as a projection to build.
     *
     * @param id the session's UUID
     * @return the count, or empty when no session has the key or it is revoked
     */
    @Query(value = "SELECT generation FROM access.session WHERE id = :id AND state = 'LIVE'", nativeQuery = true)
    Optional<Long> liveGenerationOf(UUID id);

    /**
     * The session whose chain keeps a refresh token with a hash, whatever its state.
     *
     * @param tokenHash the token's hash
     * @return the session's UUID, or empty
     */
    @Query("select s.id from Session s join s.tokens t where t.tokenHash = :tokenHash")
    Optional<UUID> holdingToken(TokenHash tokenHash);

    /**
     * An account's sessions begun under a generation older than another, live or revoked after an
     * instant.
     *
     * @param account the account's UUID
     * @param revokedTo the generation the revocation left
     * @param revokedAt when the revocation was made
     * @return their UUIDs, in no order
     */
    @Query(value = """
            SELECT id FROM access.session
            WHERE account = :account AND account_generation < :revokedTo
              AND (state = 'LIVE' OR revoked_at > :revokedAt)
            """, nativeQuery = true)
    List<UUID> endedByRevocation(UUID account, long revokedTo, Instant revokedAt);

    /**
     * Every live session.
     *
     * @return their UUIDs, in no order
     */
    @Query("select s.id from Session s where s.state = LIVE")
    List<UUID> allLive();

    /**
     * The contact times of an account's sessions within a span, both ends included.
     *
     * @param account the account
     * @param from the span's first instant
     * @param to the span's last instant
     * @return the times, in time order
     */
    @Query("""
            select c from Session s join s.contactTimes c
            where s.account = :account and c between :from and :to
            order by c
            """)
    List<Instant> contactsOf(AccountId account, Instant from, Instant to);

    /**
     * Writes a revocation over a stored session that is still live, and moves its version, with no
     * version check.
     *
     * @param id the session's UUID
     * @param revokedAt when it was revoked
     * @return 1 when a live row was revoked, 0 when none was
     */
    @Modifying
    @Query(value = """
            UPDATE access.session SET state = 'REVOKED', revoked_at = :revokedAt, version = version + 1
            WHERE id = :id AND state = 'LIVE'
            """, nativeQuery = true)
    int revoke(UUID id, Instant revokedAt);

    /**
     * Deletes every session that ended at or before an instant, its tokens and contact times with
     * it ({@code ON DELETE CASCADE}). Its end is {@code Session.endedAt}: the earlier of its
     * revocation and its current token's expiry, which PostgreSQL's {@code least} takes over
     * whichever of the two exists. A stored session holds one current token at most; the {@code
     * min} keeps a damaged one, which its load check refuses, from failing the whole statement.
     *
     * <p>It has no {@code @Modifying}: a modifying query may answer only a count, and this one
     * answers the deleted sessions' IDs through {@code DELETE … RETURNING}.
     *
     * @param cutoff the latest end that is due
     * @param lifetime a refresh token's lifetime, in ISO 8601 form
     * @return the UUIDs of the sessions deleted
     */
    @Query(value = """
            DELETE FROM access.session s
            WHERE least(
                    s.revoked_at,
                    (SELECT min(t.issued_at) + CAST(:lifetime AS interval) FROM access.refresh_token t
                     WHERE t.session_id = s.id AND t.state = 'CURRENT')) <= :cutoff
            RETURNING s.id
            """, nativeQuery = true)
    List<UUID> deleteEndedBy(Instant cutoff, String lifetime);

    /**
     * The sessions holding a contact time before an instant.
     *
     * @param cutoff the oldest contact time kept
     * @return their UUIDs, each once, in no order
     */
    @Query("select distinct s.id from Session s join s.contactTimes c where c < :cutoff")
    List<UUID> withContactsBefore(Instant cutoff);
}
