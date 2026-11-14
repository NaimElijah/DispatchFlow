package com.dispatchflow.access.domain;

import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds a {@link Session} in any state through {@code restore}, as the persistence adapter would:
 * by default live, at version 5 and session generation 2, begun under the account's generation 4,
 * with one current token issued when it opened and no contact times.
 */
final class SessionBuilder {

    static final SessionId SESSION = SessionId.of(UUID.fromString("0190a5e2-7c3b-7def-8c00-000000000001"));
    static final Version VERSION = Version.of(5);
    static final AccountGeneration ACCOUNT_GENERATION = AccountGeneration.of(4);
    static final SessionGeneration SESSION_GENERATION = SessionGeneration.of(2);
    static final Instant OPENED = Instant.parse("2027-06-01T06:00:00Z");
    static final RefreshTokenId FIRST = tokenId("1");
    static final TokenHash FIRST_HASH = new TokenHash("first");

    private SessionState state = new SessionState.Live();
    private Instant lastPasswordAt = OPENED;
    private List<Instant> contactTimes = List.of();
    private List<RefreshTokenSnapshot> tokens =
            List.of(new RefreshTokenSnapshot(FIRST, FIRST_HASH, OPENED, new TokenState.Current()));

    private SessionBuilder() {}

    static SessionBuilder aSession() {
        return new SessionBuilder();
    }

    static RefreshTokenId tokenId(String suffix) {
        return RefreshTokenId.of(
                UUID.fromString("0190a5e2-7c3b-7def-8d00-" + "0".repeat(12 - suffix.length()) + suffix));
    }

    static RefreshTokenSnapshot token(String id, String hash, Instant issuedAt, TokenState state) {
        return new RefreshTokenSnapshot(tokenId(id), new TokenHash(hash), issuedAt, state);
    }

    SessionBuilder withState(SessionState newState) {
        state = newState;
        return this;
    }

    SessionBuilder withLastPasswordAt(Instant at) {
        lastPasswordAt = at;
        return this;
    }

    SessionBuilder withContactTimes(Instant... times) {
        contactTimes = List.of(times);
        return this;
    }

    SessionBuilder withTokens(RefreshTokenSnapshot... chain) {
        tokens = List.of(chain);
        return this;
    }

    SessionSnapshot snapshot() {
        return new SessionSnapshot(
                VERSION,
                SESSION,
                AccountBuilder.ACCOUNT,
                ACCOUNT_GENERATION,
                SESSION_GENERATION,
                state,
                OPENED,
                lastPasswordAt,
                contactTimes,
                tokens);
    }

    Session build() {
        return Session.restore(snapshot());
    }
}
