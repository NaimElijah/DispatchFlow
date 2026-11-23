package com.dispatchflow.access.domain.account;

import com.dispatchflow.access.domain.AccessError;
import com.dispatchflow.access.domain.AccessError.AccountDeactivated;
import com.dispatchflow.access.domain.AccessError.AccountHasNoRole;
import com.dispatchflow.access.domain.AccessError.AccountNotDeactivated;
import com.dispatchflow.access.domain.AccessError.EmailInUse;
import com.dispatchflow.access.domain.AccessError.EmailOfAnotherAdmin;
import com.dispatchflow.access.domain.AccessError.EmailSetByAnotherAccount;
import com.dispatchflow.access.domain.AccessError.LinkAlreadyUsed;
import com.dispatchflow.access.domain.AccessError.LinkExpired;
import com.dispatchflow.access.domain.AccessError.LinkVoided;
import com.dispatchflow.access.domain.AccessError.NoRoleGiven;
import com.dispatchflow.access.domain.AccessError.NotAnAdmin;
import com.dispatchflow.access.domain.AccessError.PasswordAlreadySet;
import com.dispatchflow.access.domain.AccessError.PasswordNotSet;
import com.dispatchflow.access.domain.AccessError.RetentionNotOver;
import com.dispatchflow.access.domain.AccessError.UnknownLink;
import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.NewPassword;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.events.AccountAnonymized;
import com.dispatchflow.access.events.SessionsRevoked;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A person's sign-in identity (DL §4.1): their email address, roles, password hash, state,
 * language, token generation and credential links. One per person; the person and Admins change
 * it, rarely.
 *
 * <p>Every change to its roles, password or email, a deactivation, and an Admin ending its
 * sessions moves its {@link AccountGeneration} and raises {@link SessionsRevoked}, so every token
 * issued before is refused at once (NFR-90, NFR-177, FR-187, BR-123). Every rule is checked before
 * the state changes, so a refused call changes nothing and raises nothing.
 *
 * <p>A refusal names why sign-in or recovery can't go ahead, for the use case's own decision. The
 * sign-in and recovery endpoints must not show it: they answer a deactivated, role-less,
 * password-less ({@link PasswordNotSet}) or unknown account as they answer a wrong password, or as
 * "sent", after the same password-hash work (NFR-145, NFR-225).
 *
 * <p>The account records which other account's email-change link set its email, and when. That
 * account can't give it the Admin role; another Admin can, and doing so clears the record
 * (ADR-116). Nothing the account's own user does clears it. The address an Admin chose when
 * opening the account doesn't count: that is a new identity, not a takeover.
 */
public final class UserAccount extends AggregateRoot {

    /** How long after deactivation an account is anonymized, in Israel's calendar (NFR-258). */
    public static final Period RETENTION = Period.ofYears(1);

    /**
     * How long a link that no longer works is kept, before the next link sent drops it: the
     * longest any link works (ADR-104).
     */
    public static final Duration LINK_KEPT_FOR = Arrays.stream(LinkKind.values())
            .map(LinkKind::lifetime)
            .max(Comparator.naturalOrder())
            .orElseThrow();

    private static final Set<LinkKind> PASSWORD_LINKS = EnumSet.of(LinkKind.SET_PASSWORD, LinkKind.RECOVERY);
    private static final Set<LinkKind> EMAIL_LINKS = EnumSet.of(LinkKind.EMAIL_CHANGE);

    private final AccountId id;
    private @Nullable EmailAddress email;
    private @Nullable EmailSetByAnother emailSetByAnother;
    private RoleSet roles;
    private @Nullable PasswordHash passwordHash;
    private AccountState state;
    private Language language;
    private AccountGeneration generation;
    private @Nullable Instant termsAcceptedAt;
    private final List<CredentialLink> links;

    private UserAccount(
            @Nullable Version version,
            AccountId id,
            @Nullable EmailAddress email,
            @Nullable EmailSetByAnother emailSetByAnother,
            RoleSet roles,
            @Nullable PasswordHash passwordHash,
            AccountState state,
            Language language,
            AccountGeneration generation,
            @Nullable Instant termsAcceptedAt,
            List<CredentialLink> links) {
        super(version);
        this.id = Objects.requireNonNull(id, "id");
        this.email = email;
        this.emailSetByAnother = emailSetByAnother;
        this.roles = Objects.requireNonNull(roles, "roles");
        this.passwordHash = passwordHash;
        this.state = Objects.requireNonNull(state, "state");
        this.language = Objects.requireNonNull(language, "language");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.termsAcceptedAt = termsAcceptedAt;
        this.links = new ArrayList<>(links);
    }

    /**
     * Opens an account an Admin creates, or the written-request server step creates (FR-02,
     * FR-292). It has no password until its set-password link is used (FR-294). FR-02's "a user
     * with no role is refused" covers every role an Admin gives (ADR-104).
     *
     * @param id the new account's ID
     * @param email the address it signs in with
     * @param roles its roles, at least one
     * @param language its language, the company's default (FR-182)
     * @param holder which account holds the address now
     * @return the new account, active, raising no event
     * @throws RuleViolation with {@link NoRoleGiven} for no role (FR-02), or with {@link EmailInUse}
     *     when an account holds the address (BR-45)
     * @throws AssertionError when {@code holder} was read for another address
     */
    public static UserAccount open(
            AccountId id, EmailAddress email, RoleSet roles, Language language, EmailHolder holder) {
        Objects.requireNonNull(roles, "roles");
        if (roles.isEmpty()) {
            throw new RuleViolation(new NoRoleGiven());
        }
        requireFree(id, email, holder);
        return fresh(id, email, roles, language);
    }

    /**
     * Opens the first Admin, by the server step run when the system is deployed (FR-01, FR-291).
     * It holds the Admin role only, and gets its password through a set-password link.
     *
     * @param id the new account's ID
     * @param email the address it signs in with
     * @param language its language
     * @param activeAdmins the active Admins, read under lock
     * @param holder which account holds the address now
     * @return the new account, active, raising no event
     * @throws RuleViolation with {@link AccessError.ActiveAdminExists} when an active Admin exists
     *     (FR-291), or with {@link EmailInUse} when an account holds the address (BR-45)
     * @throws AssertionError when {@code holder} was read for another address
     */
    public static UserAccount openFirstAdmin(
            AccountId id, EmailAddress email, Language language, ActiveAdmins activeAdmins, EmailHolder holder) {
        activeAdmins.checkNoneActive();
        requireFree(id, email, holder);
        return fresh(id, email, RoleSet.of(Role.ADMIN), language);
    }

    private static void requireFree(AccountId id, EmailAddress email, EmailHolder holder) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        holder.checkIsFor(email);
        if (holder.isHeldByAnotherThan(id)) {
            throw new RuleViolation(new EmailInUse());
        }
    }

    private static UserAccount fresh(AccountId id, EmailAddress email, RoleSet roles, Language language) {
        return new UserAccount(
                null,
                id,
                email,
                null,
                roles,
                null,
                new AccountState.Active(),
                language,
                AccountGeneration.INITIAL,
                null,
                List.of());
    }

    /**
     * Rebuilds a stored account. Only the persistence adapter calls it (DL §3.5).
     *
     * @param stored the stored account
     * @return the account at its stored version
     * @throws StoredStateInvalid when an anonymized account still holds an email, a password or a
     *     link, or any other account holds no email; when an anonymized account or an Admin records
     *     that another account set its email; when it records the account itself as the one that
     *     set its email; when it was anonymized before it was deactivated; when two links share an
     *     ID or a token hash, or two open links share a kind; or when a link expires no later than
     *     it was sent
     */
    public static UserAccount restore(UserAccountSnapshot stored) {
        boolean anonymized = stored.state() instanceof AccountState.Anonymized;
        if (anonymized == stored.email().isPresent()) {
            throw invalid("an anonymized account holds no email, and every other account holds one");
        }
        if (anonymized && (stored.passwordHash().isPresent() || !stored.links().isEmpty())) {
            throw invalid("an anonymized account holds no password and no link");
        }
        if (stored.emailSetByAnother().isPresent()
                && (anonymized || stored.roles().contains(Role.ADMIN))) {
            throw invalid("an email another account set belongs to an account that holds it and no Admin role");
        }
        if (stored.emailSetByAnother()
                .filter(set -> set.by().equals(stored.id()))
                .isPresent()) {
            throw invalid("the account that set an email is another account");
        }
        if (stored.state() instanceof AccountState.Anonymized(Instant deactivatedAt, Instant anonymizedAt)
                && anonymizedAt.isBefore(deactivatedAt)) {
            throw invalid("an account is anonymized after it is deactivated");
        }
        checkLinks(stored.links());
        return new UserAccount(
                stored.version(),
                stored.id(),
                stored.email().orElse(null),
                stored.emailSetByAnother().orElse(null),
                stored.roles(),
                stored.passwordHash().orElse(null),
                stored.state(),
                stored.language(),
                stored.generation(),
                stored.termsAcceptedAt().orElse(null),
                stored.links().stream().map(CredentialLink::new).toList());
    }

    private static void checkLinks(List<CredentialLinkSnapshot> links) {
        Set<CredentialLinkId> ids = new HashSet<>();
        Set<TokenHash> hashes = new HashSet<>();
        Set<LinkKind> openKinds = EnumSet.noneOf(LinkKind.class);
        for (CredentialLinkSnapshot link : links) {
            if (!ids.add(link.id())) {
                throw invalid("two links share an ID");
            }
            if (!hashes.add(link.tokenHash())) {
                throw invalid("two links share a token hash");
            }
            if (link.status() == LinkStatus.OPEN
                    && !openKinds.add(link.purpose().kind())) {
                throw invalid("a newer link voids older open links of its kind");
            }
            if (!link.expiresAt().isAfter(link.issuedAt())) {
                throw invalid("a link expires after it is sent");
            }
        }
    }

    private static StoredStateInvalid invalid(String detail) {
        return new StoredStateInvalid(UserAccount.class, detail);
    }

    /**
     * Checks the account may sign in (BR-123, FR-53, FR-294). The password itself is checked by
     * the hashing adapter, which has no hash to check for an account whose set-password link was
     * never used, so that account is refused here rather than left to the adapter. The sign-in
     * endpoint answers each refusal here as it answers a wrong password, after the same hash work,
     * so no caller learns the account's state (NFR-145).
     *
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active; with {@link AccountHasNoRole} when it holds no
     *     role; or with {@link PasswordNotSet} when it has no password yet
     */
    public void checkSignInAllowed() {
        requireActive();
        if (roles.isEmpty()) {
            throw new RuleViolation(new AccountHasNoRole());
        }
        if (passwordHash == null) {
            throw new RuleViolation(new PasswordNotSet());
        }
    }

    /**
     * An Admin changes the account's roles (FR-02). Changing them revokes every session (NFR-90);
     * giving the roles it holds changes nothing. An Admin can't leave a user with no role; only
     * retiring a driver can (ADR-104).
     *
     * <p>Giving the Admin role to an account whose email another account's link set takes two
     * people: the Admin who set the address can't, and any other Admin can, which clears the
     * record (ADR-116). The account's own user can't clear it, because whoever holds the mailbox
     * can act as that user.
     *
     * @param newRoles the roles it will hold, at least one
     * @param activeAdmins the active Admins, read under lock
     * @param by the Admin making the change
     * @param now when
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active; with {@link NoRoleGiven} for no role (FR-02);
     *     with {@link AccessError.TooFewAdmins} when it would leave the company with fewer than
     *     two active Admins (BR-19); or with {@link EmailSetByAnotherAccount} when it would give
     *     the Admin role to an account whose email {@code by}'s email-change link set (ADR-116)
     */
    public void changeRoles(RoleSet newRoles, ActiveAdmins activeAdmins, AccountId by, Instant now) {
        Objects.requireNonNull(newRoles, "newRoles");
        Objects.requireNonNull(activeAdmins, "activeAdmins");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(now, "now");
        requireActive();
        if (newRoles.isEmpty()) {
            throw new RuleViolation(new NoRoleGiven());
        }
        if (newRoles.equals(roles)) {
            return;
        }
        if (roles.contains(Role.ADMIN) && !newRoles.contains(Role.ADMIN)) {
            activeAdmins.checkCanLose(id);
        }
        boolean promoted = !roles.contains(Role.ADMIN) && newRoles.contains(Role.ADMIN);
        if (promoted && emailSetByAnother != null && emailSetByAnother.by().equals(by)) {
            throw new RuleViolation(new EmailSetByAnotherAccount());
        }
        roles = newRoles;
        if (promoted) {
            emailSetByAnother = null;
        }
        revokeEverySession(now);
    }

    /**
     * Removes the Driver role when the driver is retired, and keeps the account's other roles
     * (FR-307; ADR-104). Only Fleet's listener on {@code DriverRetired} calls it, through Access's
     * API, after Fleet's own handler checked {@code RETIRE_DRIVER}. Delivery is at least once, so it
     * works in every state and a repeated call changes nothing. Losing the last role this way
     * leaves an account that can't sign in (BR-123).
     *
     * @param now when
     */
    public void removeDriverRole(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!roles.contains(Role.DRIVER)) {
            return;
        }
        roles = roles.without(Role.DRIVER);
        revokeEverySession(now);
    }

    /**
     * An Admin deactivates the account: it can't sign in, every session ends, every open link is
     * voided, and its history stays (FR-53, NFR-224; ADR-104).
     *
     * @param activeAdmins the active Admins, read under lock
     * @param now when
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active, or with {@link AccessError.TooFewAdmins} when
     *     it is one of two or fewer active Admins (FR-174, BR-19)
     */
    public void deactivate(ActiveAdmins activeAdmins, Instant now) {
        Objects.requireNonNull(activeAdmins, "activeAdmins");
        Objects.requireNonNull(now, "now");
        requireActive();
        if (roles.contains(Role.ADMIN)) {
            activeAdmins.checkCanLose(id);
        }
        deactivated(now);
    }

    /**
     * The server step run on a written request deactivates an Admin account the request names,
     * and ends its sessions, without the minimum of active Admins (FR-346). Like any deactivation
     * it voids every open link.
     *
     * @param now when
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active, or with {@link NotAnAdmin} when it holds no
     *     Admin role
     */
    public void deactivateOnWrittenRequest(Instant now) {
        Objects.requireNonNull(now, "now");
        requireActive();
        if (!roles.contains(Role.ADMIN)) {
            throw new RuleViolation(new NotAnAdmin());
        }
        deactivated(now);
    }

    private void deactivated(Instant now) {
        state = new AccountState.Deactivated(now);
        voidOpenLinks();
        revokeEverySession(now);
    }

    /**
     * An Admin reactivates a deactivated account, whose history stays intact (FR-297). Its
     * sessions stay ended and its links voided: the person signs in again, or recovers.
     *
     * @throws RuleViolation with {@link AccountNotDeactivated} when it is active, or with {@link
     *     AccessError.AccountAnonymized} once anonymized
     */
    public void reactivate() {
        switch (state) {
            case AccountState.Active active -> throw new RuleViolation(new AccountNotDeactivated());
            case AccountState.Anonymized anonymized -> throw new RuleViolation(new AccessError.AccountAnonymized());
            case AccountState.Deactivated deactivated -> state = new AccountState.Active();
        }
    }

    /**
     * An Admin ends every session of the account without deactivating it (FR-187), which also
     * voids every open link (NFR-224; ADR-104).
     *
     * @param now when
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active
     */
    public void endEverySession(Instant now) {
        Objects.requireNonNull(now, "now");
        requireActive();
        voidOpenLinks();
        revokeEverySession(now);
    }

    /**
     * Records a link sent to the account: a set-password link, only while it has no password
     * (FR-294), a recovery link (FR-386) or an email-change link to the new address (FR-388). It
     * voids the open links of its kind (NFR-224), and works until its kind's lifetime has passed.
     * Links that no longer work and were sent more than {@link #LINK_KEPT_FOR} ago are dropped
     * (ADR-104).
     *
     * <p>An Admin's email is changed only at that Admin's own request (FR-387, ADR-116): Admin
     * step-up alone would let one Admin point another's address at a mailbox they control, recover
     * that account and hold both of BR-19's Admins.
     *
     * <p>The recovery endpoint answers every refusal here as "sent", as it answers an address no
     * account holds, so no caller learns the account's state (NFR-145). The work behind the answer
     * differs: only a link issued and saved is sent, and no path hashes a password.
     *
     * @param purpose what the link lets its holder do
     * @param linkId the new link's ID, which no link of the account holds
     * @param tokenHash the hash of the link's token, which no link of the account holds
     * @param now when it is sent
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active; with {@link PasswordAlreadySet} for a
     *     set-password link to an account with a password (FR-294, FR-345); with {@link
     *     AccountHasNoRole} for a recovery link to an account with no role, to which recovery sends
     *     nothing (BR-123); or with {@link EmailOfAnotherAdmin} for an email change another account
     *     asked for, to an account holding the Admin role (FR-387)
     * @throws AssertionError when a link of the account already holds the ID or the hash: the use
     *     case reused one, a programming error rather than a refusal
     */
    public void issueLink(LinkPurpose purpose, CredentialLinkId linkId, TokenHash tokenHash, Instant now) {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(linkId, "linkId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(now, "now");
        if (links.stream().anyMatch(link -> link.id().equals(linkId))) {
            throw new AssertionError("a new link needs a new ID");
        }
        if (links.stream().anyMatch(link -> link.tokenHash().equals(tokenHash))) {
            throw new AssertionError("a new link needs a new hash");
        }
        requireActive();
        if (purpose.kind() == LinkKind.SET_PASSWORD && passwordHash != null) {
            throw new RuleViolation(new PasswordAlreadySet());
        }
        if (purpose.kind() == LinkKind.RECOVERY && roles.isEmpty()) {
            throw new RuleViolation(new AccountHasNoRole());
        }
        requireOwnEmailChangeForAnAdmin(purpose);
        Instant keptSince = now.minus(LINK_KEPT_FOR);
        links.removeIf(link -> !worksAt(link, now) && link.issuedAt().isBefore(keptSince));
        links.stream()
                .filter(link -> link.isOpen() && link.kind() == purpose.kind())
                .forEach(CredentialLink::voidIt);
        links.add(new CredentialLink(
                linkId, purpose, tokenHash, now, now.plus(purpose.kind().lifetime()), LinkStatus.OPEN));
    }

    private static boolean worksAt(CredentialLink link, Instant now) {
        return link.isOpen() && now.isBefore(link.expiresAt());
    }

    private void requireOwnEmailChangeForAnAdmin(LinkPurpose purpose) {
        if (purpose instanceof LinkPurpose.EmailChange change
                && roles.contains(Role.ADMIN)
                && !change.requestedBy().equals(id)) {
            throw new RuleViolation(new EmailOfAnotherAdmin());
        }
    }

    /**
     * Sets the password through a set-password or recovery link, which spends it (FR-294, FR-345,
     * NFR-224). Like any password change, it voids every other open link and revokes every session.
     *
     * @param presented the hash of the token the link carried
     * @param newHash the new password's hash, made from a checked {@link NewPassword}
     * @param now when
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active; with {@link UnknownLink} when no set-password
     *     or recovery link has the token; or with {@link LinkAlreadyUsed}, {@link LinkVoided} or
     *     {@link LinkExpired} when the link no longer works
     */
    public void setPasswordByLink(TokenHash presented, PasswordHash newHash, Instant now) {
        Objects.requireNonNull(presented, "presented");
        Objects.requireNonNull(newHash, "newHash");
        Objects.requireNonNull(now, "now");
        requireActive();
        CredentialLink link = spendable(presented, PASSWORD_LINKS, now);
        link.spend();
        passwordChanged(newHash, now);
    }

    /**
     * Confirms a new email address through the link sent to it: the address takes effect, the
     * link is spent, every other open link is voided and every session revoked (FR-388, FR-392,
     * NFR-224). An address another account holds is refused only now (FR-392). An Admin's own
     * request is checked again here, because the account may have gained the Admin role since
     * another account asked for the change (FR-387, ADR-116). The link page answers that refusal,
     * {@link EmailOfAnotherAdmin}, as it answers {@link LinkVoided}, so the holder of the new
     * mailbox learns nothing about the account's roles.
     *
     * <p>When another account asked for the change, the account records which account that was and
     * when the address took effect, so that account can't later give it the Admin role (ADR-116). A
     * change the user asked for leaves the record as it is: whoever holds the mailbox the other
     * account chose can ask as that user.
     *
     * @param presented the hash of the token the link carried
     * @param holder which account holds the new address now
     * @param now when
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active; with {@link UnknownLink} when no email-change
     *     link has the token; with {@link LinkAlreadyUsed}, {@link LinkVoided} or {@link
     *     LinkExpired} when the link no longer works; with {@link EmailOfAnotherAdmin} when the
     *     account holds the Admin role and another account asked for the change; or with {@link
     *     EmailInUse} when another account holds the address
     * @throws AssertionError when {@code holder} was read for another address than the link's
     */
    public void confirmEmailByLink(TokenHash presented, EmailHolder holder, Instant now) {
        Objects.requireNonNull(presented, "presented");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(now, "now");
        requireActive();
        CredentialLink link = spendable(presented, EMAIL_LINKS, now);
        requireOwnEmailChangeForAnAdmin(link.purpose());
        LinkPurpose.EmailChange change = (LinkPurpose.EmailChange) link.purpose();
        holder.checkIsFor(change.newEmail());
        if (holder.isHeldByAnotherThan(id)) {
            throw new RuleViolation(new EmailInUse());
        }
        link.spend();
        email = change.newEmail();
        if (!change.requestedBy().equals(id)) {
            emailSetByAnother = new EmailSetByAnother(change.requestedBy(), now);
        }
        voidOpenLinks();
        revokeEverySession(now);
    }

    /**
     * The user changes their own password, having given the current one (FR-123, FR-178). It
     * voids every open link and revokes every session (NFR-224, NFR-90).
     *
     * @param newHash the new password's hash, made from a checked {@link NewPassword}
     * @param now when
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active
     */
    public void changePassword(PasswordHash newHash, Instant now) {
        Objects.requireNonNull(newHash, "newHash");
        Objects.requireNonNull(now, "now");
        requireActive();
        passwordChanged(newHash, now);
    }

    /**
     * The user chooses their interface language, which follows them to every device (FR-124,
     * FR-181). It revokes nothing.
     *
     * @param newLanguage the language
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active
     */
    public void changeLanguage(Language newLanguage) {
        Objects.requireNonNull(newLanguage, "newLanguage");
        requireActive();
        language = newLanguage;
    }

    /**
     * Records that the user accepted the terms and the privacy notice (FR-77). The first
     * acceptance is kept; accepting again changes nothing.
     *
     * @param now when
     * @throws RuleViolation with {@link AccessError.AccountAnonymized} or {@link
     *     AccountDeactivated} when it is not active
     */
    public void acceptTerms(Instant now) {
        Objects.requireNonNull(now, "now");
        requireActive();
        if (termsAcceptedAt == null) {
            termsAcceptedAt = now;
        }
    }

    /**
     * Removes the account's personal data a year after its deactivation, whatever its roles: its
     * email, its password hash and its links, the last of which may hold a new address. The
     * record and its roles stay, because plans and audit records refer to it (NFR-258, NFR-260,
     * ADR-075). It raises {@link AccountAnonymized}, so Fleet anonymizes the driver record too
     * (ADR-104).
     *
     * @param now when the retention job runs
     * @throws RuleViolation with {@link AccountNotDeactivated} when it is active, with {@link
     *     AccessError.AccountAnonymized} when already anonymized, or with {@link RetentionNotOver}
     *     before a year has passed in Israel's calendar
     */
    public void anonymize(Instant now) {
        Objects.requireNonNull(now, "now");
        AccountState.Deactivated deactivated = switch (state) {
            case AccountState.Active active -> throw new RuleViolation(new AccountNotDeactivated());
            case AccountState.Anonymized anonymized -> throw new RuleViolation(new AccessError.AccountAnonymized());
            case AccountState.Deactivated d -> d;
        };
        Instant retentionEnds =
                deactivated.at().atZone(IsraelDateTime.ZONE).plus(RETENTION).toInstant();
        if (now.isBefore(retentionEnds)) {
            throw new RuleViolation(new RetentionNotOver());
        }
        email = null;
        emailSetByAnother = null;
        passwordHash = null;
        links.clear();
        state = new AccountState.Anonymized(deactivated.at(), now);
        raise(AccountAnonymized.class, id.value(), eventId -> new AccountAnonymized(eventId, now, id));
    }

    private void requireActive() {
        switch (state) {
            case AccountState.Active active -> {
                // The only state every command may change.
            }
            case AccountState.Deactivated deactivated -> throw new RuleViolation(new AccountDeactivated());
            case AccountState.Anonymized anonymized -> throw new RuleViolation(new AccessError.AccountAnonymized());
        }
    }

    /** The open link of one of the kinds with the presented token, checked to still work. */
    private CredentialLink spendable(TokenHash presented, Set<LinkKind> kinds, Instant now) {
        CredentialLink link = links.stream()
                .filter(candidate -> kinds.contains(candidate.kind())
                        && candidate.tokenHash().equals(presented))
                .findFirst()
                .orElseThrow(() -> new RuleViolation(new UnknownLink()));
        switch (link.status()) {
            case SPENT -> throw new RuleViolation(new LinkAlreadyUsed());
            case VOIDED -> throw new RuleViolation(new LinkVoided());
            case OPEN -> {
                if (!now.isBefore(link.expiresAt())) {
                    throw new RuleViolation(new LinkExpired());
                }
            }
        }
        return link;
    }

    private void passwordChanged(PasswordHash newHash, Instant now) {
        passwordHash = newHash;
        voidOpenLinks();
        revokeEverySession(now);
    }

    private void voidOpenLinks() {
        links.stream().filter(CredentialLink::isOpen).forEach(CredentialLink::voidIt);
    }

    private void revokeEverySession(Instant now) {
        generation = generation.next();
        long revokedAt = generation.value();
        raise(SessionsRevoked.class, id.value(), eventId -> new SessionsRevoked(eventId, now, id, revokedAt));
    }

    /**
     * The account's ID.
     *
     * @return the ID
     */
    public AccountId id() {
        return id;
    }

    /**
     * The address the account signs in with, where every link and notice goes (BR-125).
     *
     * @return the address; empty once anonymized
     */
    public Optional<EmailAddress> email() {
        return Optional.ofNullable(email);
    }

    /**
     * The roles the account holds.
     *
     * @return the roles, perhaps none (BR-123)
     */
    public RoleSet roles() {
        return roles;
    }

    /**
     * The hash the sign-in adapter checks a password against.
     *
     * @return the hash; empty until a password is set, and once anonymized
     */
    public Optional<PasswordHash> passwordHash() {
        return Optional.ofNullable(passwordHash);
    }

    /**
     * Where the account stands.
     *
     * @return active, deactivated or anonymized
     */
    public AccountState state() {
        return state;
    }

    /**
     * The account's interface language (FR-124).
     *
     * @return the language
     */
    public Language language() {
        return language;
    }

    /**
     * The account's revocation counter, which every access token carries and the token check
     * compares (NFR-177).
     *
     * @return the current generation
     */
    public AccountGeneration generation() {
        return generation;
    }

    /**
     * The account as it would be stored.
     *
     * @return a snapshot of every field, the links in the order they were sent
     */
    public UserAccountSnapshot snapshot() {
        return new UserAccountSnapshot(
                savedVersion(),
                id,
                Optional.ofNullable(email),
                Optional.ofNullable(emailSetByAnother),
                roles,
                Optional.ofNullable(passwordHash),
                state,
                language,
                generation,
                Optional.ofNullable(termsAcceptedAt),
                links.stream().map(CredentialLink::snapshot).toList());
    }
}
