package com.dispatchflow.access.domain.account;

import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The test builder of {@link UserAccount}: {@code anAccount()} is a saved, active Dispatcher opened
 * the day before {@link #SENT}, whose password was set once, so at generation 1, with no link.
 * Every state is one the factories and commands reach (ADR-127 decision 2). {@link #build} replays
 * the steps on a new account each time and saves it, so the events the steps raised are gone, the
 * version is {@link #LOADED}, and one builder may build many.
 *
 * <p>The steps run in this order, whatever order a test names them in: the account is opened with
 * its roles; its password is set unless {@link #withoutPassword}; another account's link sets its
 * address if {@link #withEmailSetBy} asks; it accepts the terms; then its links and role changes,
 * in the order named; and last its deactivation or anonymization. A link sent before a deactivation
 * is voided by it, as a deactivation voids every open link.
 */
public final class AccountBuilder {

    public static final AccountId ACCOUNT = AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-000000000001"));
    static final AccountId OTHER_ADMIN = AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-000000000002"));
    static final AccountId THIRD_ADMIN = AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-000000000003"));
    static final EmailAddress EMAIL = EmailAddress.parse("dana@example.com");
    static final EmailAddress NEW_EMAIL = EmailAddress.parse("dana.cohen@example.com");
    static final PasswordHash PASSWORD = new PasswordHash("stored-hash");
    static final Instant SENT = Instant.parse("2027-06-15T06:00:00Z");

    /** The version {@link #build} leaves: an account's first save stores 0 (ADR-126). */
    public static final Version LOADED = Version.of(0);

    /** When the account is opened and its password set. */
    static final Instant OPENED = SENT.minus(Duration.ofDays(1));

    /** The address an account whose address another account set was opened with. */
    private static final EmailAddress FIRST_EMAIL = EmailAddress.parse("dana.first@example.com");

    /** The link {@link #withEmailSetBy} sends and spends; no test names its ID or hash. */
    private static final CredentialLinkId EMAIL_SET_LINK = linkId("e0");

    private static final TokenHash EMAIL_SET_HASH = new TokenHash("email-set-by-another");
    private static final Duration SPENT_AFTER = Duration.ofSeconds(1);
    private static final ActiveAdmins NO_ADMINS = ActiveAdmins.readUnderLock(List.of());

    private RoleSet roles = RoleSet.of(Role.DISPATCHER);
    private boolean withPassword = true;
    private @Nullable EmailSetByAnother emailSetByAnother;
    private @Nullable Instant termsAcceptedAt;
    private AccountState state = new AccountState.Active();
    private final List<Consumer<UserAccount>> steps = new ArrayList<>();

    private AccountBuilder() {}

    public static AccountBuilder anAccount() {
        return new AccountBuilder();
    }

    /** An account deactivated at {@code deactivatedAt} and anonymized at {@code anonymizedAt}. */
    static AccountBuilder anAnonymizedAccount(Instant deactivatedAt, Instant anonymizedAt) {
        return anAccount().withState(new AccountState.Anonymized(deactivatedAt, anonymizedAt));
    }

    /**
     * The roles the account is opened with. None opens it as a Driver and removes the Driver role,
     * which moves its generation once more.
     */
    public AccountBuilder withRoles(Role... held) {
        roles = RoleSet.of(held);
        return this;
    }

    /**
     * Where the account stands at the end: active; deactivated at a time; or deactivated and
     * anonymized, at least a year later. An Admin is deactivated on a written request, so no
     * snapshot of the active Admins is needed.
     */
    public AccountBuilder withState(AccountState newState) {
        state = newState;
        return this;
    }

    /** No password is set, so the account can be sent a set-password link (FR-294). */
    public AccountBuilder withoutPassword() {
        withPassword = false;
        return this;
    }

    /**
     * The account's address is {@link #EMAIL}, set by {@code by}'s email-change link at {@code at}.
     * It was opened with another address, and the link it sends and spends is not one a test names.
     */
    AccountBuilder withEmailSetBy(AccountId by, Instant at) {
        emailSetByAnother = new EmailSetByAnother(by, at);
        return this;
    }

    AccountBuilder withTermsAcceptedAt(Instant at) {
        termsAcceptedAt = at;
        return this;
    }

    /** An open link of the purpose, sent at {@link #SENT}, with its kind's lifetime. */
    AccountBuilder withOpenLink(String id, LinkPurpose purpose, String tokenHash) {
        return withOpenLink(id, purpose, tokenHash, SENT);
    }

    /** An open link of the purpose, sent at {@code sentAt}; it voids the open links of its kind. */
    AccountBuilder withOpenLink(String id, LinkPurpose purpose, String tokenHash, Instant sentAt) {
        steps.add(account -> account.issueLink(purpose, linkId(id), new TokenHash(tokenHash), sentAt));
        return this;
    }

    /**
     * A link sent at {@link #SENT} and spent a second later: a set-password or recovery link sets
     * {@link #PASSWORD}, and an email-change link sets its address. Either voids every other open
     * link and moves the generation.
     */
    AccountBuilder withSpentLink(String id, LinkPurpose purpose, String tokenHash) {
        return withSpentLink(id, purpose, tokenHash, SENT);
    }

    /** As {@link #withSpentLink(String, LinkPurpose, String)}, sent at {@code sentAt}. */
    AccountBuilder withSpentLink(String id, LinkPurpose purpose, String tokenHash, Instant sentAt) {
        withOpenLink(id, purpose, tokenHash, sentAt);
        TokenHash hash = new TokenHash(tokenHash);
        Instant spentAt = sentAt.plus(SPENT_AFTER);
        steps.add(account -> {
            if (purpose instanceof LinkPurpose.EmailChange change) {
                account.confirmEmailByLink(hash, EmailHolder.nobody(change.newEmail()), spentAt);
            } else {
                account.setPasswordByLink(hash, PASSWORD, spentAt);
            }
        });
        return this;
    }

    /**
     * A link sent at {@link #SENT} and voided a second later by an Admin ending every session, which
     * voids every open link and moves the generation.
     */
    AccountBuilder withVoidedLink(String id, LinkPurpose purpose, String tokenHash) {
        withOpenLink(id, purpose, tokenHash, SENT);
        steps.add(account -> account.endEverySession(SENT.plus(SPENT_AFTER)));
        return this;
    }

    /** {@code by} changes the account's roles at {@link #SENT}, after the links named before it. */
    AccountBuilder withRolesChangedTo(AccountId by, Role... newRoles) {
        steps.add(account -> account.changeRoles(RoleSet.of(newRoles), NO_ADMINS, by, SENT));
        return this;
    }

    static CredentialLinkId linkId(String suffix) {
        return CredentialLinkId.of(
                UUID.fromString("0190a5e2-7c3b-7def-8b00-" + "0".repeat(12 - suffix.length()) + suffix));
    }

    /** A new account that ran every step, saved as a use case would have saved it. */
    public UserAccount build() {
        UserAccount account = opened();
        if (withPassword) {
            account.changePassword(PASSWORD, OPENED);
        }
        EmailSetByAnother setBy = emailSetByAnother;
        if (setBy != null) {
            account.issueLink(
                    new LinkPurpose.EmailChange(EMAIL, setBy.by()), EMAIL_SET_LINK, EMAIL_SET_HASH, setBy.at());
            account.confirmEmailByLink(EMAIL_SET_HASH, EmailHolder.nobody(EMAIL), setBy.at());
        }
        Instant accepted = termsAcceptedAt;
        if (accepted != null) {
            account.acceptTerms(accepted);
        }
        steps.forEach(step -> step.accept(account));
        switch (state) {
            case AccountState.Active active -> {
                // Opened active: nothing more to do.
            }
            case AccountState.Deactivated deactivated -> deactivate(account, deactivated.at());
            case AccountState.Anonymized anonymized -> {
                deactivate(account, anonymized.deactivatedAt());
                account.anonymize(anonymized.anonymizedAt());
            }
        }
        return asSaved(account);
    }

    private UserAccount opened() {
        EmailAddress address = emailSetByAnother == null ? EMAIL : FIRST_EMAIL;
        RoleSet openedWith = roles.isEmpty() ? RoleSet.of(Role.DRIVER) : roles;
        UserAccount account =
                UserAccount.open(ACCOUNT, address, openedWith, Language.HEBREW, EmailHolder.nobody(address));
        if (roles.isEmpty()) {
            account.removeDriverRole(OPENED);
        }
        return account;
    }

    private static void deactivate(UserAccount account, Instant at) {
        if (account.roles().contains(Role.ADMIN)) {
            account.deactivateOnWrittenRequest(at);
        } else {
            account.deactivate(NO_ADMINS, at);
        }
    }
}
