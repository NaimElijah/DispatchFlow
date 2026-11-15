package com.dispatchflow.access.domain.session;

import com.dispatchflow.access.domain.TokenHash;
import java.time.Instant;
import java.util.Objects;

/**
 * A refresh token as stored: what {@link Session#snapshot()} gives and {@link Session#restore}
 * takes for each token of the chain. Only the token's hash is kept (NFR-91). The adapter builds
 * its values as {@link SessionSnapshot} says, inside {@code StoredStateInvalid.whileRestoring}.
 *
 * @param id the token's ID
 * @param tokenHash the token's hash
 * @param issuedAt when it was issued, which its 14 days count from (NFR-87)
 * @param state where it stands
 */
public record RefreshTokenSnapshot(RefreshTokenId id, TokenHash tokenHash, Instant issuedAt, TokenState state) {

    /** Checks every part is present. */
    public RefreshTokenSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(state, "state");
    }
}
