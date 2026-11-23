package com.dispatchflow.access.domain.session;

import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.account.AccountBuilder;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The test builder of {@link Session}: {@code aSession()} is a saved, live session that {@code
 * AccountBuilder.anAccount()} signed in at {@link #OPENED}, under {@link #ACCOUNT_GENERATION}, with
 * one current token, {@link #FIRST}, and no contact time. Every state is one the factory and the
 * commands reach (ADR-127 decision 2). {@link #build} replays the steps on a new session each time
 * and saves it, so its version is {@link #LOADED} and one builder may build many.
 *
 * <p>The steps run in the order named, and a revocation last, whatever order a test names it in,
 * since a revoked session takes no contact, refresh or step-up.
 */
final class SessionBuilder {

    static final SessionId SESSION = SessionId.of(UUID.fromString("0190a5e2-7c3b-7def-8c00-000000000001"));

    /** The version {@link #build} leaves: a session's first save stores 0 (ADR-126). */
    static final Version LOADED = Version.of(0);

    /** The generation of the account every built session signed in, which it began under. */
    static final AccountGeneration ACCOUNT_GENERATION =
            AccountBuilder.anAccount().build().generation();

    static final Instant OPENED = Instant.parse("2027-06-01T06:00:00Z");
    static final RefreshTokenId FIRST = tokenId("1");
    static final TokenHash FIRST_HASH = new TokenHash("first");

    /** The token {@link #steppedUpAt} issues; no test presents it. */
    private static final RefreshTokenId STEP_UP_TOKEN = tokenId("f");

    private static final TokenHash STEP_UP_HASH = new TokenHash("stepped-up");

    private Instant openedAt = OPENED;
    private final List<Consumer<Session>> steps = new ArrayList<>();
    private @Nullable Instant revokedAt;

    private SessionBuilder() {}

    static SessionBuilder aSession() {
        return new SessionBuilder();
    }

    static RefreshTokenId tokenId(String suffix) {
        return RefreshTokenId.of(
                UUID.fromString("0190a5e2-7c3b-7def-8d00-" + "0".repeat(12 - suffix.length()) + suffix));
    }

    /** What a test expects of a token: its ID by suffix, its hash's text, its issue and its state. */
    static TokenDescription token(String id, String hash, Instant issuedAt, TokenState state) {
        return new TokenDescription(tokenId(id), new TokenHash(hash), issuedAt, state);
    }

    /** The session signs in at {@code at}, with {@link #FIRST} issued then. */
    SessionBuilder openedAt(Instant at) {
        openedAt = at;
        return this;
    }

    /** The token hashed {@code presented} is exchanged at {@code at} for token {@code newId}. */
    SessionBuilder exchanged(String presented, String newId, String newHash, Instant at) {
        steps.add(session -> session.exchange(
                new TokenHash(presented), ACCOUNT_GENERATION, tokenId(newId), new TokenHash(newHash), at));
        return this;
    }

    /** The session gives the password at {@code at}, which supersedes its current token with {@code newId}. */
    SessionBuilder steppedUp(String newId, String newHash, Instant at) {
        steps.add(session -> session.confirmPassword(ACCOUNT_GENERATION, tokenId(newId), new TokenHash(newHash), at));
        return this;
    }

    /** As {@link #steppedUp}, with a token no test presents. */
    SessionBuilder steppedUpAt(Instant at) {
        steps.add(session -> session.confirmPassword(ACCOUNT_GENERATION, STEP_UP_TOKEN, STEP_UP_HASH, at));
        return this;
    }

    /**
     * Requests at each time, in order, each recorded as a contact: so the minute's spacing and the
     * 14 days' drop apply, as they do to every contact.
     */
    SessionBuilder withContactTimes(Instant... times) {
        for (Instant time : times) {
            steps.add(session -> session.recordContact(time));
        }
        return this;
    }

    /** Live, or signed out at a time, after every other step. */
    SessionBuilder withState(SessionState newState) {
        revokedAt = newState instanceof SessionState.Revoked(Instant at) ? at : null;
        return this;
    }

    /** A new session that ran every step, saved as a use case would have saved it. */
    Session build() {
        Session session = Session.open(SESSION, AccountBuilder.anAccount().build(), FIRST, FIRST_HASH, openedAt);
        steps.forEach(step -> step.accept(session));
        Instant revoked = revokedAt;
        if (revoked != null) {
            session.revoke(revoked);
        }
        return asSaved(session);
    }
}
