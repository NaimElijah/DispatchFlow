package com.dispatchflow.access.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One emailed link of an account, an entity inside {@link UserAccount} (DL §4.1): what it lets its
 * holder do, its token's hash, when it expires and whether it is still open. Only the account
 * changes it.
 */
final class CredentialLink {

    private final CredentialLinkId id;
    private final LinkPurpose purpose;
    private final TokenHash tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private LinkStatus status;

    CredentialLink(
            CredentialLinkId id,
            LinkPurpose purpose,
            TokenHash tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            LinkStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.status = Objects.requireNonNull(status, "status");
    }

    CredentialLink(CredentialLinkSnapshot stored) {
        this(stored.id(), stored.purpose(), stored.tokenHash(), stored.issuedAt(), stored.expiresAt(), stored.status());
    }

    LinkPurpose purpose() {
        return purpose;
    }

    LinkKind kind() {
        return purpose.kind();
    }

    TokenHash tokenHash() {
        return tokenHash;
    }

    Instant issuedAt() {
        return issuedAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    LinkStatus status() {
        return status;
    }

    boolean isOpen() {
        return status == LinkStatus.OPEN;
    }

    void spend() {
        status = LinkStatus.SPENT;
    }

    void voidIt() {
        status = LinkStatus.VOIDED;
    }

    CredentialLinkSnapshot snapshot() {
        return new CredentialLinkSnapshot(id, purpose, tokenHash, issuedAt, expiresAt, status);
    }
}
