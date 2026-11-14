package com.dispatchflow.access.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An account as stored: what {@link UserAccount#snapshot()} gives and {@link UserAccount#restore}
 * takes. Its values are already checked; {@code restore} checks how they fit together.
 *
 * @param version the stored version
 * @param id the account's ID
 * @param email the address it signs in with; absent once anonymized
 * @param roles its roles, perhaps none (BR-123)
 * @param passwordHash its password's hash; absent until a password is set, and once anonymized
 * @param state active, deactivated or anonymized
 * @param language its interface language (FR-124)
 * @param generation its revocation counter (NFR-177)
 * @param termsAcceptedAt when it accepted the terms and privacy notice, if it has (FR-77)
 * @param links its credential links, in the order they were sent
 */
public record UserAccountSnapshot(
        Version version,
        AccountId id,
        Optional<EmailAddress> email,
        RoleSet roles,
        Optional<PasswordHash> passwordHash,
        AccountState state,
        Language language,
        AccountGeneration generation,
        Optional<Instant> termsAcceptedAt,
        List<CredentialLinkSnapshot> links) {

    /** Checks every part is present, and copies the links. */
    public UserAccountSnapshot {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(termsAcceptedAt, "termsAcceptedAt");
        links = List.copyOf(links);
    }
}
