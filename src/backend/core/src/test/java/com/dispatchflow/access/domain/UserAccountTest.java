package com.dispatchflow.access.domain;

import static com.dispatchflow.access.domain.AccountBuilder.ACCOUNT;
import static com.dispatchflow.access.domain.AccountBuilder.EMAIL;
import static com.dispatchflow.access.domain.AccountBuilder.GENERATION;
import static com.dispatchflow.access.domain.AccountBuilder.NEW_EMAIL;
import static com.dispatchflow.access.domain.AccountBuilder.OTHER_ADMIN;
import static com.dispatchflow.access.domain.AccountBuilder.SENT;
import static com.dispatchflow.access.domain.AccountBuilder.THIRD_ADMIN;
import static com.dispatchflow.access.domain.AccountBuilder.anAccount;
import static com.dispatchflow.access.domain.AccountBuilder.anAnonymizedAccount;
import static com.dispatchflow.access.domain.AccountBuilder.link;
import static com.dispatchflow.access.domain.AccountBuilder.linkId;
import static com.dispatchflow.access.domain.AccountBuilder.openLink;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.access.domain.AccessError.AccountAnonymized;
import com.dispatchflow.access.domain.AccessError.AccountDeactivated;
import com.dispatchflow.access.domain.AccessError.AccountHasNoRole;
import com.dispatchflow.access.domain.AccessError.AccountNotDeactivated;
import com.dispatchflow.access.domain.AccessError.ActiveAdminExists;
import com.dispatchflow.access.domain.AccessError.EmailInUse;
import com.dispatchflow.access.domain.AccessError.EmailOfAnotherAdmin;
import com.dispatchflow.access.domain.AccessError.LinkAlreadyUsed;
import com.dispatchflow.access.domain.AccessError.LinkExpired;
import com.dispatchflow.access.domain.AccessError.LinkVoided;
import com.dispatchflow.access.domain.AccessError.NoRoleGiven;
import com.dispatchflow.access.domain.AccessError.NotAnAdmin;
import com.dispatchflow.access.domain.AccessError.PasswordAlreadySet;
import com.dispatchflow.access.domain.AccessError.PasswordNotSet;
import com.dispatchflow.access.domain.AccessError.RetentionNotOver;
import com.dispatchflow.access.domain.AccessError.TooFewAdmins;
import com.dispatchflow.access.domain.AccessError.UnknownLink;
import com.dispatchflow.access.events.SessionsRevoked;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class UserAccountTest {

    private static final AccountId NEW_ACCOUNT = AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-00000000000a"));
    private static final Instant NOW = SENT.plus(Duration.ofMinutes(10));
    private static final ActiveAdmins NO_ADMINS = ActiveAdmins.readUnderLock(List.of());
    private static final ActiveAdmins TWO_ADMINS = ActiveAdmins.readUnderLock(List.of(ACCOUNT, OTHER_ADMIN));
    private static final ActiveAdmins THREE_ADMINS =
            ActiveAdmins.readUnderLock(List.of(ACCOUNT, OTHER_ADMIN, THIRD_ADMIN));
    private static final TokenHash SET_PASSWORD_TOKEN = new TokenHash("set-password-token");
    private static final TokenHash RECOVERY_TOKEN = new TokenHash("recovery-token");
    private static final TokenHash EMAIL_TOKEN = new TokenHash("email-token");
    private static final PasswordHash NEW_PASSWORD = new PasswordHash("new-hash");

    /** Asserts the refusal through the shared helper, and that it changed nothing. */
    private static void assertRefusedUnchanged(UserAccount account, ThrowingCallable call, DomainError expected) {
        UserAccountSnapshot before = account.snapshot();

        assertRefused(call, expected);

        assertThat(account.snapshot()).isEqualTo(before);
        assertThat(account.pendingEvents()).isEmpty();
    }

    private static SessionsRevoked revoked(int ordinal, Instant at) {
        return new SessionsRevoked(
                new EventId("SessionsRevoked", ACCOUNT.value(), Version.of(4), ordinal),
                at,
                ACCOUNT,
                GENERATION.next().value());
    }

    private static void assertAllLinks(UserAccount account, LinkStatus status) {
        assertThat(account.snapshot().links())
                .isNotEmpty()
                .extracting(CredentialLinkSnapshot::status)
                .containsOnly(status);
    }

    private static void assertRevokedOnce(UserAccount account) {
        assertThat(account.generation()).isEqualTo(GENERATION.next());
        assertThat(account.pendingEvents()).containsExactly(revoked(0, NOW));
    }

    @Test
    void GivenAnAccountWithAPasswordAndAnEmailChangeLink_WhenItsSnapshotIsPrinted_ThenNoAddressHashOrTokenShows() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();

        assertThat(account.snapshot().toString())
                .doesNotContain(EMAIL.value(), NEW_EMAIL.value(), AccountBuilder.PASSWORD.value(), EMAIL_TOKEN.value());
    }

    /** Asserts the call fails as a programming error with the message, and changed nothing. */
    private static void assertBugUnchanged(UserAccount account, ThrowingCallable call, String message) {
        UserAccountSnapshot before = account.snapshot();

        assertThatThrownBy(call).isInstanceOf(AssertionError.class).hasMessage(message);

        assertThat(account.snapshot()).isEqualTo(before);
        assertThat(account.pendingEvents()).isEmpty();
    }

    @Test
    void GivenASnapshotOfActiveAdminsThatLacksTheAccount_WhenItStopsBeingAnAdmin_ThenTheBugIsReported() {
        UserAccount account = anAccount().withRoles(Role.ADMIN).build();
        ActiveAdmins others = ActiveAdmins.readUnderLock(List.of(OTHER_ADMIN, THIRD_ADMIN));
        String message = "the active Admins read under lock must include the active Admin that changes";

        assertBugUnchanged(account, () -> account.changeRoles(RoleSet.of(Role.DISPATCHER), others, NOW), message);
        assertBugUnchanged(account, () -> account.deactivate(others, NOW), message);
    }

    @Test
    void GivenAnEmailHolderReadForAnotherAddress_WhenOpenedOrConfirmed_ThenTheBugIsReported() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();
        String message = "the email holder was read for another address";

        assertThatThrownBy(() -> UserAccount.open(
                        NEW_ACCOUNT, EMAIL, RoleSet.of(Role.DRIVER), Language.HEBREW, EmailHolder.nobody(NEW_EMAIL)))
                .isInstanceOf(AssertionError.class)
                .hasMessage(message);
        assertBugUnchanged(
                account, () -> account.confirmEmailByLink(EMAIL_TOKEN, EmailHolder.nobody(EMAIL), NOW), message);
    }

    @Test
    void GivenALinkIdTheAccountHolds_WhenANewLinkIsIssuedWithIt_ThenTheBugIsReported() {
        UserAccount account = anAccount()
                .withLink(link("1", new LinkPurpose.Recovery(), "spent", LinkStatus.SPENT))
                .build();

        assertBugUnchanged(
                account,
                () -> account.issueLink(new LinkPurpose.Recovery(), linkId("1"), RECOVERY_TOKEN, NOW),
                "a new link needs a new ID");
    }

    @Test
    void GivenATokenHashALinkOfTheAccountHolds_WhenANewLinkIsIssuedWithIt_ThenTheBugIsReported() {
        UserAccount account = anAccount()
                .withLink(link("1", new LinkPurpose.Recovery(), RECOVERY_TOKEN.value(), LinkStatus.SPENT))
                .build();

        assertBugUnchanged(
                account,
                () -> account.issueLink(new LinkPurpose.Recovery(), linkId("2"), RECOVERY_TOKEN, NOW),
                "a new link needs a new hash");
    }

    // Opening an account

    @Test
    void GivenAFreeAddressAndARole_WhenOpened_ThenTheAccountIsActiveWithNoPasswordAndRaisesNothing() {
        UserAccount account = UserAccount.open(
                NEW_ACCOUNT, EMAIL, RoleSet.of(Role.DRIVER), Language.ENGLISH, EmailHolder.nobody(EMAIL));

        assertThat(account.version()).isEqualTo(Version.NEW);
        assertThat(account.state()).isEqualTo(new AccountState.Active());
        assertThat(account.email()).contains(EMAIL);
        assertThat(account.roles()).isEqualTo(RoleSet.of(Role.DRIVER));
        assertThat(account.passwordHash()).isEmpty();
        assertThat(account.language()).isEqualTo(Language.ENGLISH);
        assertThat(account.generation()).isEqualTo(AccountGeneration.INITIAL);
        assertThat(account.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoRole_WhenOpened_ThenNoRoleGiven() {
        assertRefused(
                () -> UserAccount.open(NEW_ACCOUNT, EMAIL, RoleSet.of(), Language.HEBREW, EmailHolder.nobody(EMAIL)),
                new NoRoleGiven());
    }

    @Test
    void GivenAnAddressAnotherAccountHolds_WhenOpened_ThenEmailInUse() {
        assertRefused(
                () -> UserAccount.open(
                        NEW_ACCOUNT,
                        EMAIL,
                        RoleSet.of(Role.DRIVER),
                        Language.HEBREW,
                        EmailHolder.account(EMAIL, ACCOUNT)),
                new EmailInUse());
    }

    @Test
    void GivenNoActiveAdmin_WhenTheFirstAdminIsOpened_ThenItHoldsOnlyTheAdminRole() {
        UserAccount first =
                UserAccount.openFirstAdmin(NEW_ACCOUNT, EMAIL, Language.HEBREW, NO_ADMINS, EmailHolder.nobody(EMAIL));

        assertThat(first.roles()).isEqualTo(RoleSet.of(Role.ADMIN));
        assertThat(first.state()).isEqualTo(new AccountState.Active());
        assertThat(first.passwordHash()).isEmpty();
    }

    @Test
    void GivenAnActiveAdmin_WhenTheFirstAdminIsOpened_ThenActiveAdminExists() {
        ActiveAdmins one = ActiveAdmins.readUnderLock(List.of(ACCOUNT));

        assertRefused(
                () -> UserAccount.openFirstAdmin(
                        NEW_ACCOUNT, NEW_EMAIL, Language.HEBREW, one, EmailHolder.nobody(NEW_EMAIL)),
                new ActiveAdminExists());
    }

    @Test
    void GivenNoActiveAdminButAHeldAddress_WhenTheFirstAdminIsOpened_ThenEmailInUse() {
        assertRefused(
                () -> UserAccount.openFirstAdmin(
                        NEW_ACCOUNT, EMAIL, Language.HEBREW, NO_ADMINS, EmailHolder.account(EMAIL, ACCOUNT)),
                new EmailInUse());
    }

    // Signing in

    @Test
    void GivenAnActiveAccountWithARole_WhenSignInIsChecked_ThenItIsAllowed() {
        UserAccount account = anAccount().build();

        account.checkSignInAllowed();

        assertThat(account.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAnActiveAccountWithNoRole_WhenSignInIsChecked_ThenAccountHasNoRole() {
        UserAccount account = anAccount().withRoles().build();

        assertRefusedUnchanged(account, account::checkSignInAllowed, new AccountHasNoRole());
    }

    @Test
    void GivenAnActiveAccountWithNoPasswordYet_WhenSignInIsChecked_ThenPasswordNotSet() {
        UserAccount account = anAccount().withPassword(Optional.empty()).build();

        assertRefusedUnchanged(account, account::checkSignInAllowed, new PasswordNotSet());
    }

    @Test
    void GivenADeactivatedAccount_WhenSignInIsChecked_ThenAccountDeactivated() {
        UserAccount account =
                anAccount().withState(new AccountState.Deactivated(SENT)).build();

        assertRefusedUnchanged(account, account::checkSignInAllowed, new AccountDeactivated());
    }

    @Test
    void GivenAnAnonymizedAccount_WhenSignInIsChecked_ThenAccountAnonymized() {
        UserAccount account = anAnonymizedAccount(SENT, NOW).build();

        assertRefusedUnchanged(account, account::checkSignInAllowed, new AccountAnonymized());
    }

    // Roles

    @Test
    void GivenADispatcher_WhenGivenTheDriverRoleToo_ThenItHoldsBothAndEverySessionIsRevoked() {
        UserAccount account = anAccount().build();

        account.changeRoles(RoleSet.of(Role.DISPATCHER, Role.DRIVER), TWO_ADMINS, NOW);

        assertThat(account.roles()).isEqualTo(RoleSet.of(Role.DISPATCHER, Role.DRIVER));
        assertRevokedOnce(account);
    }

    @Test
    void GivenTheRolesItHolds_WhenRolesAreChanged_ThenNothingChangesAndNothingIsRevoked() {
        UserAccount account = anAccount().build();

        account.changeRoles(RoleSet.of(Role.DISPATCHER), TWO_ADMINS, NOW);

        assertThat(account.generation()).isEqualTo(GENERATION);
        assertThat(account.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAnyAccount_WhenAnAdminTakesEveryRole_ThenNoRoleGiven() {
        UserAccount account = anAccount().build();

        assertRefusedUnchanged(account, () -> account.changeRoles(RoleSet.of(), TWO_ADMINS, NOW), new NoRoleGiven());
    }

    @Test
    void GivenOneOfTwoActiveAdmins_WhenItLosesTheAdminRole_ThenTooFewAdmins() {
        UserAccount account = anAccount().withRoles(Role.ADMIN).build();

        assertRefusedUnchanged(
                account, () -> account.changeRoles(RoleSet.of(Role.DISPATCHER), TWO_ADMINS, NOW), new TooFewAdmins(2));
    }

    @Test
    void GivenOneOfThreeActiveAdmins_WhenItLosesTheAdminRole_ThenItKeepsTheOthers() {
        UserAccount account = anAccount().withRoles(Role.ADMIN, Role.DISPATCHER).build();

        account.changeRoles(RoleSet.of(Role.DISPATCHER), THREE_ADMINS, NOW);

        assertThat(account.roles()).isEqualTo(RoleSet.of(Role.DISPATCHER));
        assertRevokedOnce(account);
    }

    @Test
    void GivenOneOfTwoActiveAdmins_WhenItGainsARole_ThenTheMinimumIsNotChecked() {
        UserAccount account = anAccount().withRoles(Role.ADMIN).build();

        account.changeRoles(RoleSet.of(Role.ADMIN, Role.DRIVER), TWO_ADMINS, NOW);

        assertThat(account.roles()).isEqualTo(RoleSet.of(Role.ADMIN, Role.DRIVER));
    }

    @Test
    void GivenADriverWhoDispatches_WhenTheDriverRoleIsRemoved_ThenItKeepsDispatcherAndEverySessionIsRevoked() {
        UserAccount account =
                anAccount().withRoles(Role.DISPATCHER, Role.DRIVER).build();

        account.removeDriverRole(NOW);

        assertThat(account.roles()).isEqualTo(RoleSet.of(Role.DISPATCHER));
        assertRevokedOnce(account);
    }

    @Test
    void GivenOneOfTwoActiveAdminsWhoDrives_WhenTheDriverRoleIsRemoved_ThenItStaysAnAdmin() {
        UserAccount account = anAccount().withRoles(Role.ADMIN, Role.DRIVER).build();

        account.removeDriverRole(NOW);

        assertThat(account.roles()).isEqualTo(RoleSet.of(Role.ADMIN));
    }

    @Test
    void GivenADriverOnly_WhenTheDriverRoleIsRemoved_ThenItHoldsNoRoleAndCanNoLongerSignIn() {
        UserAccount account = anAccount().withRoles(Role.DRIVER).build();

        account.removeDriverRole(NOW);

        assertThat(account.roles().isEmpty()).isTrue();
        assertRevokedOnce(account);
        assertRefused(account::checkSignInAllowed, new AccountHasNoRole());
    }

    @Test
    void GivenAnAccountWithoutTheDriverRole_WhenTheDriverRoleIsRemovedAgain_ThenNothingChanges() {
        UserAccount account = anAccount().build();

        account.removeDriverRole(NOW);

        assertThat(account.snapshot()).isEqualTo(anAccount().snapshot());
        assertThat(account.pendingEvents()).isEmpty();
    }

    @Test
    void GivenADeactivatedDriver_WhenTheDriverRoleIsRemoved_ThenItIsRemoved() {
        UserAccount account = anAccount()
                .withRoles(Role.DRIVER)
                .withState(new AccountState.Deactivated(SENT))
                .build();

        account.removeDriverRole(NOW);

        assertThat(account.roles().isEmpty()).isTrue();
    }

    // Deactivation

    @Test
    void GivenAnActiveDispatcher_WhenDeactivated_ThenItIsDeactivatedAndEverySessionIsRevoked() {
        UserAccount account = anAccount().build();

        account.deactivate(TWO_ADMINS, NOW);

        assertThat(account.state()).isEqualTo(new AccountState.Deactivated(NOW));
        assertRevokedOnce(account);
    }

    @Test
    void GivenOneOfTwoActiveAdmins_WhenDeactivated_ThenTooFewAdmins() {
        UserAccount account = anAccount().withRoles(Role.ADMIN).build();

        assertRefusedUnchanged(account, () -> account.deactivate(TWO_ADMINS, NOW), new TooFewAdmins(2));
    }

    @Test
    void GivenOneOfThreeActiveAdmins_WhenDeactivated_ThenItIsDeactivated() {
        UserAccount account = anAccount().withRoles(Role.ADMIN).build();

        account.deactivate(THREE_ADMINS, NOW);

        assertThat(account.state()).isEqualTo(new AccountState.Deactivated(NOW));
    }

    @Test
    void GivenTheOnlyActiveAdmin_WhenDeactivatedOnAWrittenRequest_ThenTheMinimumIsSkipped() {
        UserAccount account = anAccount().withRoles(Role.ADMIN).build();

        account.deactivateOnWrittenRequest(NOW);

        assertThat(account.state()).isEqualTo(new AccountState.Deactivated(NOW));
        assertRevokedOnce(account);
    }

    @Test
    void GivenADispatcher_WhenDeactivatedOnAWrittenRequest_ThenNotAnAdmin() {
        UserAccount account = anAccount().build();

        assertRefusedUnchanged(account, () -> account.deactivateOnWrittenRequest(NOW), new NotAnAdmin());
    }

    @Test
    void GivenADeactivatedAccount_WhenReactivated_ThenItIsActiveWithItsGenerationKept() {
        UserAccount account =
                anAccount().withState(new AccountState.Deactivated(SENT)).build();

        account.reactivate();

        assertThat(account.state()).isEqualTo(new AccountState.Active());
        assertThat(account.generation()).isEqualTo(GENERATION);
        assertThat(account.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAnActiveAccount_WhenReactivated_ThenAccountNotDeactivated() {
        UserAccount account = anAccount().build();

        assertRefusedUnchanged(account, account::reactivate, new AccountNotDeactivated());
    }

    @Test
    void GivenAnActiveAccount_WhenAnAdminEndsEverySession_ThenEverySessionIsRevokedAndItStaysActive() {
        UserAccount account = anAccount().build();

        account.endEverySession(NOW);

        assertThat(account.state()).isEqualTo(new AccountState.Active());
        assertRevokedOnce(account);
    }

    // Links

    @Test
    void GivenAnAccountWithNoPassword_WhenASetPasswordLinkIsIssued_ThenItIsOpenFor72Hours() {
        UserAccount account = anAccount().withPassword(Optional.empty()).build();

        account.issueLink(new LinkPurpose.SetPassword(), linkId("1"), SET_PASSWORD_TOKEN, NOW);

        assertThat(account.snapshot().links())
                .containsExactly(new CredentialLinkSnapshot(
                        linkId("1"),
                        new LinkPurpose.SetPassword(),
                        SET_PASSWORD_TOKEN,
                        NOW,
                        NOW.plus(Duration.ofHours(72)),
                        LinkStatus.OPEN));
        assertThat(account.pendingEvents()).isEmpty();
    }

    @Test
    void
            GivenAnOpenRecoveryLinkAndASetPasswordLink_WhenANewRecoveryLinkIsIssued_ThenOnlyTheOlderRecoveryLinkIsVoided() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.Recovery(), "old-recovery"))
                .withLink(openLink("2", new LinkPurpose.SetPassword(), "set-password"))
                .build();

        account.issueLink(new LinkPurpose.Recovery(), linkId("3"), RECOVERY_TOKEN, NOW);

        assertThat(account.snapshot().links())
                .extracting(CredentialLinkSnapshot::status)
                .containsExactly(LinkStatus.VOIDED, LinkStatus.OPEN, LinkStatus.OPEN);
        assertThat(account.snapshot().links().get(2).expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void GivenAnAccountWithNoRole_WhenARecoveryLinkIsIssued_ThenAccountHasNoRole() {
        UserAccount account = anAccount().withRoles().build();

        assertRefusedUnchanged(
                account,
                () -> account.issueLink(new LinkPurpose.Recovery(), linkId("1"), RECOVERY_TOKEN, NOW),
                new AccountHasNoRole());
    }

    @Test
    void
            GivenAnOpenSetPasswordLink_WhenUsedBeforeItExpires_ThenThePasswordIsSetEveryOtherLinkIsVoidedAndSessionsAreRevoked() {
        UserAccount account = anAccount()
                .withPassword(Optional.empty())
                .withLink(openLink("1", new LinkPurpose.SetPassword(), SET_PASSWORD_TOKEN.value()))
                .withLink(openLink("2", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();

        account.setPasswordByLink(SET_PASSWORD_TOKEN, NEW_PASSWORD, NOW);

        assertThat(account.passwordHash()).contains(NEW_PASSWORD);
        assertThat(account.snapshot().links())
                .extracting(CredentialLinkSnapshot::status)
                .containsExactly(LinkStatus.SPENT, LinkStatus.VOIDED);
        assertRevokedOnce(account);
    }

    @Test
    void GivenAnAccountWithAPassword_WhenASetPasswordLinkIsIssued_ThenPasswordAlreadySet() {
        UserAccount account = anAccount().build();

        assertRefusedUnchanged(
                account,
                () -> account.issueLink(new LinkPurpose.SetPassword(), linkId("1"), SET_PASSWORD_TOKEN, NOW),
                new PasswordAlreadySet());
    }

    @Test
    void GivenLinksThatNoLongerWorkSentOver72HoursAgo_WhenANewLinkIsIssued_ThenTheyAreDroppedAndTheRestKept() {
        Instant sent = NOW.minus(UserAccount.LINK_KEPT_FOR).minusSeconds(1);
        UserAccount account = anAccount()
                .withLink(new CredentialLinkSnapshot(
                        linkId("1"),
                        new LinkPurpose.Recovery(),
                        new TokenHash("old-spent"),
                        sent,
                        sent.plus(Duration.ofHours(1)),
                        LinkStatus.SPENT))
                .withLink(new CredentialLinkSnapshot(
                        linkId("2"),
                        new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT),
                        new TokenHash("old-expired"),
                        sent,
                        sent.plus(Duration.ofHours(1)),
                        LinkStatus.OPEN))
                .withLink(link("3", new LinkPurpose.SetPassword(), "recent-voided", LinkStatus.VOIDED))
                .build();

        account.issueLink(new LinkPurpose.Recovery(), linkId("4"), RECOVERY_TOKEN, NOW);

        assertThat(account.snapshot().links())
                .extracting(CredentialLinkSnapshot::id)
                .containsExactly(linkId("3"), linkId("4"));
    }

    @Test
    void GivenAnOpenRecoveryLink_WhenUsed_ThenThePasswordIsSetAndEverySessionIsRevoked() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.Recovery(), RECOVERY_TOKEN.value()))
                .build();

        account.setPasswordByLink(RECOVERY_TOKEN, NEW_PASSWORD, NOW);

        assertThat(account.passwordHash()).contains(NEW_PASSWORD);
        assertRevokedOnce(account);
    }

    @Test
    void GivenASetPasswordLink_WhenTheAccountIsDeactivatedAndReactivated_ThenTheLinkIsVoided() {
        UserAccount account = anAccount()
                .withPassword(Optional.empty())
                .withLink(openLink("1", new LinkPurpose.SetPassword(), SET_PASSWORD_TOKEN.value()))
                .build();

        account.deactivate(THREE_ADMINS, NOW);
        account.reactivate();

        assertAllLinks(account, LinkStatus.VOIDED);
        assertRefused(() -> account.setPasswordByLink(SET_PASSWORD_TOKEN, NEW_PASSWORD, NOW), new LinkVoided());
    }

    @Test
    void GivenAnEmailChangeLink_WhenAnAdminEndsEverySession_ThenTheLinkIsVoided() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();

        account.endEverySession(NOW);

        assertAllLinks(account, LinkStatus.VOIDED);
        assertRefused(
                () -> account.confirmEmailByLink(EMAIL_TOKEN, EmailHolder.nobody(NEW_EMAIL), NOW), new LinkVoided());
    }

    @Test
    void GivenASetPasswordLinkAtItsExpiry_WhenUsed_ThenLinkExpired() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.SetPassword(), SET_PASSWORD_TOKEN.value()))
                .build();
        Instant expiry = SENT.plus(Duration.ofHours(72));

        assertRefusedUnchanged(
                account, () -> account.setPasswordByLink(SET_PASSWORD_TOKEN, NEW_PASSWORD, expiry), new LinkExpired());
    }

    @Test
    void GivenASetPasswordLinkOneSecondBeforeItsExpiry_WhenUsed_ThenThePasswordIsSet() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.SetPassword(), SET_PASSWORD_TOKEN.value()))
                .build();

        account.setPasswordByLink(
                SET_PASSWORD_TOKEN,
                NEW_PASSWORD,
                SENT.plus(Duration.ofHours(72)).minusSeconds(1));

        assertThat(account.passwordHash()).contains(NEW_PASSWORD);
    }

    @Test
    void GivenASpentLink_WhenUsedAgain_ThenLinkAlreadyUsed() {
        UserAccount account = anAccount()
                .withLink(link("1", new LinkPurpose.SetPassword(), SET_PASSWORD_TOKEN.value(), LinkStatus.SPENT))
                .build();

        assertRefusedUnchanged(
                account, () -> account.setPasswordByLink(SET_PASSWORD_TOKEN, NEW_PASSWORD, NOW), new LinkAlreadyUsed());
    }

    @Test
    void GivenAVoidedLink_WhenUsed_ThenLinkVoided() {
        UserAccount account = anAccount()
                .withLink(link("1", new LinkPurpose.Recovery(), RECOVERY_TOKEN.value(), LinkStatus.VOIDED))
                .build();

        assertRefusedUnchanged(
                account, () -> account.setPasswordByLink(RECOVERY_TOKEN, NEW_PASSWORD, NOW), new LinkVoided());
    }

    @Test
    void GivenATokenOfNoLink_WhenUsedToSetAPassword_ThenUnknownLink() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.SetPassword(), SET_PASSWORD_TOKEN.value()))
                .build();

        assertRefusedUnchanged(
                account,
                () -> account.setPasswordByLink(new TokenHash("no-such-token"), NEW_PASSWORD, NOW),
                new UnknownLink());
    }

    @Test
    void GivenAnEmailChangeLink_WhenUsedToSetAPassword_ThenUnknownLink() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();

        assertRefusedUnchanged(
                account, () -> account.setPasswordByLink(EMAIL_TOKEN, NEW_PASSWORD, NOW), new UnknownLink());
    }

    @Test
    void
            GivenAnEmailChangeLinkToAFreeAddress_WhenUsed_ThenTheNewAddressTakesEffectEveryOtherLinkIsVoidedAndSessionsAreRevoked() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .withLink(openLink("2", new LinkPurpose.Recovery(), RECOVERY_TOKEN.value()))
                .build();

        account.confirmEmailByLink(EMAIL_TOKEN, EmailHolder.nobody(NEW_EMAIL), NOW);

        assertThat(account.email()).contains(NEW_EMAIL);
        assertThat(account.snapshot().links())
                .extracting(CredentialLinkSnapshot::status)
                .containsExactly(LinkStatus.SPENT, LinkStatus.VOIDED);
        assertRevokedOnce(account);
    }

    @Test
    void GivenAnAdmin_WhenAnotherAdminSendsAnEmailChangeLinkForThem_ThenEmailOfAnotherAdmin() {
        UserAccount account = anAccount().withRoles(Role.ADMIN).build();

        assertRefusedUnchanged(
                account,
                () -> account.issueLink(
                        new LinkPurpose.EmailChange(NEW_EMAIL, OTHER_ADMIN), linkId("1"), EMAIL_TOKEN, NOW),
                new EmailOfAnotherAdmin());
    }

    @Test
    void GivenAnAdmin_WhenTheyAskToChangeTheirOwnEmail_ThenTheLinkIsSentToTheNewAddress() {
        UserAccount account = anAccount().withRoles(Role.ADMIN).build();

        account.issueLink(new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), linkId("1"), EMAIL_TOKEN, NOW);

        assertThat(account.snapshot().links())
                .containsExactly(new CredentialLinkSnapshot(
                        linkId("1"),
                        new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT),
                        EMAIL_TOKEN,
                        NOW,
                        NOW.plus(Duration.ofHours(1)),
                        LinkStatus.OPEN));
    }

    @Test
    void GivenADispatcher_WhenAnAdminSendsAnEmailChangeLinkForThem_ThenTheLinkIsSent() {
        UserAccount account = anAccount().build();

        account.issueLink(new LinkPurpose.EmailChange(NEW_EMAIL, OTHER_ADMIN), linkId("1"), EMAIL_TOKEN, NOW);

        assertAllLinks(account, LinkStatus.OPEN);
    }

    @Test
    void GivenAnEmailChangeAnotherAccountAskedForBeforeThisOneBecameAnAdmin_WhenUsed_ThenEmailOfAnotherAdmin() {
        UserAccount account = anAccount()
                .withRoles(Role.ADMIN, Role.DISPATCHER)
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, OTHER_ADMIN), EMAIL_TOKEN.value()))
                .build();

        assertRefusedUnchanged(
                account,
                () -> account.confirmEmailByLink(EMAIL_TOKEN, EmailHolder.nobody(NEW_EMAIL), NOW),
                new EmailOfAnotherAdmin());
    }

    @Test
    void GivenAnAdminsOwnEmailChange_WhenUsed_ThenTheNewAddressTakesEffect() {
        UserAccount account = anAccount()
                .withRoles(Role.ADMIN)
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();

        account.confirmEmailByLink(EMAIL_TOKEN, EmailHolder.nobody(NEW_EMAIL), NOW);

        assertThat(account.email()).contains(NEW_EMAIL);
    }

    @Test
    void GivenAnEmailChangeLinkToAnAddressAnotherAccountTookMeanwhile_WhenUsed_ThenEmailInUse() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();

        assertRefusedUnchanged(
                account,
                () -> account.confirmEmailByLink(EMAIL_TOKEN, EmailHolder.account(NEW_EMAIL, OTHER_ADMIN), NOW),
                new EmailInUse());
    }

    @Test
    void GivenAnEmailChangeLinkOneSecondBeforeItsHour_WhenUsed_ThenTheNewAddressTakesEffect() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();

        account.confirmEmailByLink(
                EMAIL_TOKEN,
                EmailHolder.nobody(NEW_EMAIL),
                SENT.plus(Duration.ofHours(1)).minusSeconds(1));

        assertThat(account.email()).contains(NEW_EMAIL);
    }

    @Test
    void GivenAnEmailChangeLinkAtItsHour_WhenUsed_ThenLinkExpired() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();

        assertRefusedUnchanged(
                account,
                () -> account.confirmEmailByLink(
                        EMAIL_TOKEN, EmailHolder.nobody(NEW_EMAIL), SENT.plus(Duration.ofHours(1))),
                new LinkExpired());
    }

    @Test
    void GivenARecoveryLink_WhenUsedToConfirmAnEmail_ThenUnknownLink() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.Recovery(), RECOVERY_TOKEN.value()))
                .build();

        assertRefusedUnchanged(
                account,
                () -> account.confirmEmailByLink(RECOVERY_TOKEN, EmailHolder.nobody(NEW_EMAIL), NOW),
                new UnknownLink());
    }

    // Password, language and terms

    @Test
    void GivenOpenLinks_WhenThePasswordIsChanged_ThenEveryLinkIsVoidedAndSessionsAreRevoked() {
        UserAccount account = anAccount()
                .withLink(openLink("1", new LinkPurpose.Recovery(), RECOVERY_TOKEN.value()))
                .withLink(openLink("2", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();

        account.changePassword(NEW_PASSWORD, NOW);

        assertThat(account.passwordHash()).contains(NEW_PASSWORD);
        assertThat(account.snapshot().links())
                .extracting(CredentialLinkSnapshot::status)
                .containsOnly(LinkStatus.VOIDED);
        assertRevokedOnce(account);
    }

    @Test
    void GivenAnAccount_WhenItsLanguageIsChanged_ThenNothingIsRevoked() {
        UserAccount account = anAccount().build();

        account.changeLanguage(Language.ENGLISH);

        assertThat(account.language()).isEqualTo(Language.ENGLISH);
        assertThat(account.generation()).isEqualTo(GENERATION);
        assertThat(account.pendingEvents()).isEmpty();
    }

    @Test
    void GivenTermsNotYetAccepted_WhenAccepted_ThenTheTimeIsRecorded() {
        UserAccount account = anAccount().build();

        account.acceptTerms(NOW);

        assertThat(account.snapshot().termsAcceptedAt()).contains(NOW);
    }

    @Test
    void GivenTermsAlreadyAccepted_WhenAcceptedAgain_ThenTheFirstAcceptanceStays() {
        UserAccount account = anAccount().withTermsAcceptedAt(SENT).build();

        account.acceptTerms(NOW);

        assertThat(account.snapshot().termsAcceptedAt()).contains(SENT);
    }

    // Anonymization

    @Test
    void GivenAnAccountDeactivatedAYearAgo_WhenAnonymized_ThenItsPersonalDataIsGoneAndItsRolesStay() {
        Instant deactivated = Instant.parse("2026-06-15T06:00:00Z");
        Instant aYearLater = Instant.parse("2027-06-15T06:00:00Z");
        UserAccount account = anAccount()
                .withRoles(Role.DRIVER)
                .withState(new AccountState.Deactivated(deactivated))
                .withLink(openLink("1", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()))
                .build();

        account.anonymize(aYearLater);

        assertThat(account.state()).isEqualTo(new AccountState.Anonymized(deactivated, aYearLater));
        assertThat(account.email()).isEmpty();
        assertThat(account.passwordHash()).isEmpty();
        assertThat(account.snapshot().links()).isEmpty();
        assertThat(account.roles()).isEqualTo(RoleSet.of(Role.DRIVER));
        assertThat(account.pendingEvents())
                .containsExactly(new com.dispatchflow.access.events.AccountAnonymized(
                        new EventId("AccountAnonymized", ACCOUNT.value(), Version.of(4), 0), aYearLater, ACCOUNT));
    }

    @Test
    void GivenAnAccountDeactivatedTheNightBeforeTheClocksChange_WhenAYearHasPassedInIsrael_ThenItIsAnonymized() {
        // 01:30 on 27 March 2026 is winter time in Israel; on 27 March 2027 summer time has begun,
        // so a year later in Israel is 22:30Z, an hour short of 365 days.
        UserAccount account = anAccount()
                .withState(new AccountState.Deactivated(Instant.parse("2026-03-26T23:30:00Z")))
                .build();

        account.anonymize(Instant.parse("2027-03-26T22:30:00Z"));

        assertThat(account.state()).isInstanceOf(AccountState.Anonymized.class);
    }

    @Test
    void
            GivenAnAccountDeactivatedTheNightBeforeTheClocksChange_WhenOneSecondShortOfAYearInIsrael_ThenRetentionNotOver() {
        UserAccount account = anAccount()
                .withState(new AccountState.Deactivated(Instant.parse("2026-03-26T23:30:00Z")))
                .build();

        assertRefusedUnchanged(
                account, () -> account.anonymize(Instant.parse("2027-03-26T22:29:59Z")), new RetentionNotOver());
    }

    @Test
    void GivenAnAccountDeactivatedLessThanAYearAgo_WhenAnonymized_ThenRetentionNotOver() {
        UserAccount account = anAccount()
                .withState(new AccountState.Deactivated(Instant.parse("2026-06-15T06:00:00Z")))
                .build();

        assertRefusedUnchanged(
                account, () -> account.anonymize(Instant.parse("2027-06-15T05:59:59Z")), new RetentionNotOver());
    }

    @Test
    void GivenAnActiveAccount_WhenAnonymized_ThenAccountNotDeactivated() {
        UserAccount account = anAccount().build();

        assertRefusedUnchanged(account, () -> account.anonymize(NOW), new AccountNotDeactivated());
    }
}
