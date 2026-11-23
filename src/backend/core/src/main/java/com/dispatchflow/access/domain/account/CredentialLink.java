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
 * changes it: its getters are public, and its methods that change it are not.
 *
 * <p>It is mapped for JPA (ADR-126): its {@link LinkPurpose} is stored as its {@link LinkKind},
 * plus the new address and the requesting account that only an email-change link has.
 */
@Entity
@Table(schema = "access", name = "credential_link")
public class CredentialLink {

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

    /**
     * The link's ID.
     *
     * @return the ID
     */
    public CredentialLinkId id() {
        return CredentialLinkId.of(id);
    }

    /**
     * What the link lets its holder do.
     *
     * @return the purpose; an email change names the new address and who asked
     */
    public LinkPurpose purpose() {
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

    /**
     * The hash of the link's token, all that is kept of it (NFR-211).
     *
     * @return the hash, which prints redacted
     */
    public TokenHash tokenHash() {
        return tokenHash;
    }

    /**
     * When the link was sent.
     *
     * @return the time
     */
    public Instant issuedAt() {
        return issuedAt;
    }

    /**
     * When the link stops working: its kind's lifetime after it was sent.
     *
     * @return the time
     */
    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * Whether the link is open, spent or voided; an open link past its expiry no longer works.
     *
     * @return the status
     */
    public LinkStatus status() {
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

    /**
     * A form that leaves out the token's hash and any new address.
     *
     * @return the link without its token or address
     */
    @Override
    public String toString() {
        return "CredentialLink[id=" + id() + ", kind=" + kind + ", issuedAt=" + issuedAt + ", expiresAt=" + expiresAt
                + ", status=" + status + "]";
    }
}
