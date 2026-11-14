package com.dispatchflow.access.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A session as stored: what {@link Session#snapshot()} gives and {@link Session#restore} takes.
 * Its values are already checked; {@code restore} checks how they fit together.
 *
 * @param version the stored version
 * @param id the session's ID
 * @param account the account it signed in
 * @param accountGeneration the account's generation when it began; a newer one ends it (NFR-177)
 * @param generation its own generation, which giving the password moves (NFR-227)
 * @param state live or revoked
 * @param openedAt when it signed in
 * @param lastPasswordAt when it last gave the password: at sign-in, or for a step-up (NFR-214)
 * @param contactTimes its contact times, oldest first (NFR-280)
 * @param tokens its chain of refresh tokens, in the order they were issued
 */
public record SessionSnapshot(
        Version version,
        SessionId id,
        AccountId account,
        AccountGeneration accountGeneration,
        SessionGeneration generation,
        SessionState state,
        Instant openedAt,
        Instant lastPasswordAt,
        List<Instant> contactTimes,
        List<RefreshTokenSnapshot> tokens) {

    /** Checks every part is present, and copies the lists. */
    public SessionSnapshot {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(accountGeneration, "accountGeneration");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(openedAt, "openedAt");
        Objects.requireNonNull(lastPasswordAt, "lastPasswordAt");
        contactTimes = List.copyOf(contactTimes);
        tokens = List.copyOf(tokens);
    }
}
