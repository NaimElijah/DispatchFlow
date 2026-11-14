package com.dispatchflow.access.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a {@link UserAccount} in any state through {@code restore}, as the persistence adapter
 * would: by default an active Dispatcher with a password, no links, at version 3 and generation 4.
 */
final class AccountBuilder {

    static final AccountId ACCOUNT = AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-000000000001"));
    static final AccountId OTHER_ADMIN = AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-000000000002"));
    static final AccountId THIRD_ADMIN = AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-000000000003"));
    static final EmailAddress EMAIL = EmailAddress.parse("dana@example.com");
    static final EmailAddress NEW_EMAIL = EmailAddress.parse("dana.cohen@example.com");
    static final PasswordHash PASSWORD = new PasswordHash("stored-hash");
    static final Version VERSION = Version.of(3);
    static final AccountGeneration GENERATION = AccountGeneration.of(4);
    static final Instant SENT = Instant.parse("2027-06-15T06:00:00Z");

    private Optional<EmailAddress> email = Optional.of(EMAIL);
    private RoleSet roles = RoleSet.of(Role.DISPATCHER);
    private Optional<PasswordHash> passwordHash = Optional.of(PASSWORD);
    private AccountState state = new AccountState.Active();
    private Optional<Instant> termsAcceptedAt = Optional.empty();
    private final List<CredentialLinkSnapshot> links = new ArrayList<>();

    private AccountBuilder() {}

    static AccountBuilder anAccount() {
        return new AccountBuilder();
    }

    /** An account anonymized as {@code anonymize} leaves one: no email, password or links. */
    static AccountBuilder anAnonymizedAccount(Instant deactivatedAt, Instant anonymizedAt) {
        return anAccount()
                .withState(new AccountState.Anonymized(deactivatedAt, anonymizedAt))
                .withEmail(Optional.empty())
                .withPassword(Optional.empty());
    }

    AccountBuilder withRoles(Role... held) {
        roles = RoleSet.of(held);
        return this;
    }

    AccountBuilder withState(AccountState newState) {
        state = newState;
        return this;
    }

    AccountBuilder withEmail(Optional<EmailAddress> newEmail) {
        email = newEmail;
        return this;
    }

    AccountBuilder withPassword(Optional<PasswordHash> newHash) {
        passwordHash = newHash;
        return this;
    }

    AccountBuilder withTermsAcceptedAt(Instant at) {
        termsAcceptedAt = Optional.of(at);
        return this;
    }

    AccountBuilder withLink(CredentialLinkSnapshot link) {
        links.add(link);
        return this;
    }

    /** An open link of the purpose, sent at {@link #SENT}, with its kind's lifetime. */
    static CredentialLinkSnapshot openLink(String id, LinkPurpose purpose, String tokenHash) {
        return link(id, purpose, tokenHash, LinkStatus.OPEN);
    }

    static CredentialLinkSnapshot link(String id, LinkPurpose purpose, String tokenHash, LinkStatus status) {
        return new CredentialLinkSnapshot(
                linkId(id),
                purpose,
                new TokenHash(tokenHash),
                SENT,
                SENT.plus(purpose.kind().lifetime()),
                status);
    }

    static CredentialLinkId linkId(String suffix) {
        return CredentialLinkId.of(
                UUID.fromString("0190a5e2-7c3b-7def-8b00-" + "0".repeat(12 - suffix.length()) + suffix));
    }

    UserAccountSnapshot snapshot() {
        return new UserAccountSnapshot(
                VERSION,
                ACCOUNT,
                email,
                roles,
                passwordHash,
                state,
                Language.HEBREW,
                GENERATION,
                termsAcceptedAt,
                links);
    }

    UserAccount build() {
        return UserAccount.restore(snapshot());
    }
}
