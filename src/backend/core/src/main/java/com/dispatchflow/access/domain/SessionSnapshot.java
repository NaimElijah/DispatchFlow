package com.dispatchflow.access.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A session as stored: what {@link Session#snapshot()} gives and {@link Session#restore} takes.
 * Its values are already checked; {@code restore} checks how they fit together.
 *
 * <p>Its contact times are personal data (NFR-280), so it is {@link PersonalData} and {@link
 * #toString()} prints only how many it holds (ADR-106).
 *
 * <p>The persistence adapter builds every stored value inside {@link
 * com.dispatchflow.shared.StoredStateInvalid#whileRestoring}, so a stored value the rules now
 * refuse is an incident, never a user-facing refusal.
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
        List<RefreshTokenSnapshot> tokens)
        implements PersonalData {

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

    /**
     * A form that leaves the contact times out and names how many there are.
     *
     * @return the snapshot without its contact times
     */
    @Override
    public String toString() {
        return "SessionSnapshot[version=" + version + ", id=" + id + ", account=" + account + ", accountGeneration="
                + accountGeneration + ", generation=" + generation + ", state=" + state + ", openedAt=" + openedAt
                + ", lastPasswordAt=" + lastPasswordAt + ", contactTimes=" + contactTimes.size() + " redacted, tokens="
                + tokens + "]";
    }
}
