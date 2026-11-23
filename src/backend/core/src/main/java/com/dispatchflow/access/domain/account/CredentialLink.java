package com.dispatchflow.access.domain.account;

import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.shared.AccountId;
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
 * One emailed link of an account, an entity inside {@link UserAccount} (DL §4.1): what it lets its
 * holder do, its token's hash, when it expires and whether it is still open. Only the account
 * changes it.
 *
 * <p>It is mapped for JPA (ADR-126): its {@link LinkPurpose} is stored as its {@link LinkKind},
 * plus the new address and the requesting account that only an email-change link has.
 */
@Entity
@Table(schema = "access", name = "credential_link")
class CredentialLink {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private LinkKind kind;

    private @Nullable EmailAddress newEmail;

    private @Nullable AccountId requestedBy;

    private TokenHash tokenHash;

    private Instant issuedAt;

    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    private LinkStatus status;

    /** For Hibernate, which sets the fields from the stored row. */
    protected CredentialLink() {}

    CredentialLink(
            CredentialLinkId id,
            LinkPurpose purpose,
            TokenHash tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            LinkStatus status) {
        this.id = Objects.requireNonNull(id, "id").value();
        this.kind = Objects.requireNonNull(purpose, "purpose").kind();
        if (purpose instanceof LinkPurpose.EmailChange(EmailAddress email, AccountId by)) {
            this.newEmail = email;
            this.requestedBy = by;
        }
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.status = Objects.requireNonNull(status, "status");
    }

    /** Builds a link {@link UserAccount#restore} rebuilds; it goes with the snapshots (ADR-126). */
    CredentialLink(CredentialLinkSnapshot stored) {
        this(stored.id(), stored.purpose(), stored.tokenHash(), stored.issuedAt(), stored.expiresAt(), stored.status());
    }

    CredentialLinkId id() {
        return CredentialLinkId.of(id);
    }

    LinkPurpose purpose() {
        return switch (kind) {
            case SET_PASSWORD -> new LinkPurpose.SetPassword();
            case RECOVERY -> new LinkPurpose.Recovery();
            case EMAIL_CHANGE ->
                new LinkPurpose.EmailChange(
                        Objects.requireNonNull(newEmail, "an email-change link has a new address"),
                        Objects.requireNonNull(requestedBy, "an email-change link has a requester"));
        };
    }

    LinkKind kind() {
        return kind;
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
        return new CredentialLinkSnapshot(id(), purpose(), tokenHash, issuedAt, expiresAt, status);
    }
}
