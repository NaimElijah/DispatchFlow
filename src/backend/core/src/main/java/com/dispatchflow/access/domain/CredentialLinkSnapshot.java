package com.dispatchflow.access.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A credential link as stored: what {@link UserAccount#snapshot()} gives and {@link
 * UserAccount#restore} takes for each link. Only the token's hash is kept (NFR-211). The adapter
 * builds its values as {@link UserAccountSnapshot} says, inside {@code
 * StoredStateInvalid.whileRestoring}.
 *
 * @param id the link's ID
 * @param purpose what the link lets its holder do
 * @param tokenHash the hash of the link's token
 * @param issuedAt when the link was sent
 * @param expiresAt when it stops working
 * @param status whether it is open, spent or voided
 */
public record CredentialLinkSnapshot(
        CredentialLinkId id,
        LinkPurpose purpose,
        TokenHash tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        LinkStatus status) {

    /** Checks every part is present. */
    public CredentialLinkSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
    }
}
