package com.dispatchflow.access.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One refresh token of a session's chain, an entity inside {@link Session} (DL §4.1): its hash,
 * when it was issued and where it stands. Only the session changes it.
 */
final class RefreshToken {

    private final RefreshTokenId id;
    private final TokenHash tokenHash;
    private final Instant issuedAt;
    private TokenState state;

    RefreshToken(RefreshTokenId id, TokenHash tokenHash, Instant issuedAt, TokenState state) {
        this.id = Objects.requireNonNull(id, "id");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.state = Objects.requireNonNull(state, "state");
    }

    RefreshToken(RefreshTokenSnapshot stored) {
        this(stored.id(), stored.tokenHash(), stored.issuedAt(), stored.state());
    }

    RefreshTokenId id() {
        return id;
    }

    TokenHash tokenHash() {
        return tokenHash;
    }

    Instant issuedAt() {
        return issuedAt;
    }

    TokenState state() {
        return state;
    }

    void moveTo(TokenState newState) {
        state = Objects.requireNonNull(newState, "newState");
    }

    RefreshTokenSnapshot snapshot() {
        return new RefreshTokenSnapshot(id, tokenHash, issuedAt, state);
    }
}
