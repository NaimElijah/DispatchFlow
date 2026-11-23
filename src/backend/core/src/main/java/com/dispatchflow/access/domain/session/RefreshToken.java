package com.dispatchflow.access.domain.session;

import com.dispatchflow.access.domain.TokenHash;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One refresh token of a session's chain, an entity inside {@link Session} (DL §4.1): its hash,
 * when it was issued and where it stands. Only the session changes it.
 *
 * <p>It is mapped for JPA (ADR-126): its {@link TokenState} is stored as a kind, the time every
 * state but current carries, and the successor only an exchanged token names.
 */
@Entity
@Table(schema = "access", name = "refresh_token")
class RefreshToken {

    /** Which {@link TokenState} a token is in. */
    private enum StateKind {
        CURRENT,
        EXCHANGED,
        REPLACED,
        SUPERSEDED
    }

    @Id
    private UUID id;

    private TokenHash tokenHash;

    private Instant issuedAt;

    @Enumerated(EnumType.STRING)
    private StateKind state;

    private @Nullable Instant stateAt;

    private @Nullable UUID successor;

    /** For Hibernate, which sets the fields from the stored row. */
    protected RefreshToken() {}

    RefreshToken(RefreshTokenId id, TokenHash tokenHash, Instant issuedAt, TokenState state) {
        this.id = Objects.requireNonNull(id, "id").value();
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        moveTo(state);
    }

    /** Builds a token {@link Session#restore} rebuilds; it goes with the snapshots (ADR-126). */
    RefreshToken(RefreshTokenSnapshot stored) {
        this(stored.id(), stored.tokenHash(), stored.issuedAt(), stored.state());
    }

    RefreshTokenId id() {
        return RefreshTokenId.of(id);
    }

    TokenHash tokenHash() {
        return tokenHash;
    }

    Instant issuedAt() {
        return issuedAt;
    }

    TokenState state() {
        return switch (state) {
            case CURRENT -> new TokenState.Current();
            case EXCHANGED ->
                new TokenState.Exchanged(
                        stateTime(),
                        RefreshTokenId.of(Objects.requireNonNull(successor, "an exchange names its successor")));
            case REPLACED -> new TokenState.Replaced(stateTime());
            case SUPERSEDED -> new TokenState.Superseded(stateTime());
        };
    }

    /** Moves the token to another state, in place, clearing what the old one carried. */
    void moveTo(TokenState newState) {
        Objects.requireNonNull(newState, "newState");
        stateAt = null;
        successor = null;
        state = switch (newState) {
            case TokenState.Current current -> StateKind.CURRENT;
            case TokenState.Exchanged exchanged -> {
                stateAt = exchanged.at();
                successor = exchanged.successor().value();
                yield StateKind.EXCHANGED;
            }
            case TokenState.Replaced replaced -> {
                stateAt = replaced.at();
                yield StateKind.REPLACED;
            }
            case TokenState.Superseded superseded -> {
                stateAt = superseded.at();
                yield StateKind.SUPERSEDED;
            }
        };
    }

    RefreshTokenSnapshot snapshot() {
        return new RefreshTokenSnapshot(id(), tokenHash, issuedAt, state());
    }

    private Instant stateTime() {
        return Objects.requireNonNull(stateAt, "every token state but current has its time");
    }
}
