package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.session.Session;
import com.dispatchflow.access.domain.session.SessionGeneration;
import com.dispatchflow.access.domain.session.SessionState;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps sessions in {@code access.session}, their refresh tokens in {@code access.refresh_token}
 * and their contact times in {@code access.session_contact}.
 *
 * <p>A session's contact times are excluded from its optimistic lock, so a save that only kept or
 * dropped them flushes them with no version check and moves no version (DL §4.1). The session
 * tells which save it needs through {@link Session#changedBeyondContactTimes()}.
 *
 * <p>{@link #liveGenerationOf} reads the database on every call: the revocation cache the port
 * names is not built yet.
 */
@Component
@Transactional
class JpaSessionRepository implements SessionRepository {

    private final Sessions sessions;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param sessions the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which flushes the saves that move
     *     no version and detaches a session whose revocation it writes
     */
    JpaSessionRepository(Sessions sessions, VersionedSave versionedSave, EntityManager entityManager) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<Session> get(SessionId id) {
        return load(id.value());
    }

    @Override
    public Optional<SessionGeneration> liveGenerationOf(SessionId session) {
        return sessions.liveGenerationOf(session.value()).map(SessionGeneration::of);
    }

    @Override
    public Optional<Session> withRefreshToken(TokenHash token) {
        return sessions.holdingToken(token).flatMap(this::load);
    }

    @Override
    public List<Session> endedByRevocation(AccountId account, AccountGeneration revokedTo, Instant revokedAt) {
        return sessions.endedByRevocation(account.value(), revokedTo.value(), revokedAt).stream()
                .map(this::load)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public List<SessionId> allLive() {
        return ids(sessions.allLive());
    }

    @Override
    public List<Instant> contactsOf(AccountId account, Instant from, Instant to) {
        return sessions.contactsOf(account, from, to);
    }

    @Override
    public void add(Session session) {
        sessions.save(session);
        entityManager.flush();
    }

    /**
     * {@inheritDoc}
     *
     * <p>A session whose change only kept or dropped contact times is flushed as it is: nothing
     * checks or moves its version, because those times are excluded from its optimistic lock. Any
     * other change goes through the shared save.
     */
    @Override
    public void save(Session session) {
        if (session.changedBeyondContactTimes()) {
            versionedSave.save(session, sessions::save);
            return;
        }
        sessions.save(session);
        entityManager.flush();
    }

    /**
     * {@inheritDoc}
     *
     * <p>It detaches the session first, so the persistence context never writes the rest of its
     * change, and then revokes the stored row with one native {@code UPDATE}.
     */
    @Override
    public void saveRevocation(Session session) {
        if (!(session.state() instanceof SessionState.Revoked(Instant revokedAt))) {
            throw new IllegalArgumentException("a live session is saved through save, not saveRevocation");
        }
        entityManager.detach(session);
        sessions.revoke(session.id().value(), revokedAt);
    }

    @Override
    public List<SessionId> deleteEndedBefore(Instant cutoff) {
        return ids(sessions.deleteEndedBy(cutoff, Session.REFRESH_TOKEN_LIFETIME.toString()));
    }

    @Override
    public List<SessionId> withContactsBefore(Instant cutoff) {
        return ids(sessions.withContactsBefore(cutoff));
    }

    /** Loads a session whole: its refresh tokens with it, then its contact times into the same instance. */
    private Optional<Session> load(UUID id) {
        Optional<Session> session = sessions.findById(id);
        session.ifPresent(found -> sessions.withContactTimes(id));
        return session;
    }

    private static List<SessionId> ids(List<UUID> keys) {
        return keys.stream().map(SessionId::of).toList();
    }
}
