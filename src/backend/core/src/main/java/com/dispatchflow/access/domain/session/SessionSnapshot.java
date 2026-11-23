package com.dispatchflow.access.domain.session;

import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A session as stored: what {@link Session#snapshot()} gives and {@link Session#restore} takes.
 * Its values are already checked; {@code restore} checks how they fit together.
 *
 * <p>Its contact times are personal data (NFR-280), so it is {@link PersonalData} and {@link
 * #toString()} prints only how many it holds (ADR-106). It prints only how many refresh tokens it
 * holds too, because each token's times are times the device was in use, and it hides the sign-in,
 * password and revocation times for the same reason.
 *
 * <p>The persistence adapter builds every stored value inside {@link
 * com.dispatchflow.shared.StoredStateInvalid#whileRestoring}, so a stored value the rules now
 * refuse is an incident, never a user-facing refusal.
 *
 * @param version the version it was loaded at, or null for an aggregate never saved
 *     (ADR-126)
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
        @Nullable Version version,
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

    private static final String REDACTED = "redacted";

    /** Checks every part is present, and copies the lists. */
    public SessionSnapshot {
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
     * A form that hides every time the session holds: when it signed in, when it last gave the
     * password and when it was revoked are times the user was active, as its contact times are. It
     * names how many contact times and refresh tokens there are.
     *
     * @return the snapshot without its times and tokens
     */
    @Override
    public String toString() {
        return "SessionSnapshot[version=" + version + ", id=" + id + ", account=" + account + ", accountGeneration="
                + accountGeneration + ", generation=" + generation + ", state=" + redacted(state) + ", openedAt="
                + REDACTED + ", lastPasswordAt=" + REDACTED + ", contactTimes=" + contactTimes.size() + " " + REDACTED
                + ", tokens=" + tokens.size() + " " + REDACTED + "]";
    }

    private static String redacted(SessionState state) {
        return switch (state) {
            case SessionState.Live live -> live.toString();
            case SessionState.Revoked revoked -> "Revoked[at=" + REDACTED + "]";
        };
    }
}
