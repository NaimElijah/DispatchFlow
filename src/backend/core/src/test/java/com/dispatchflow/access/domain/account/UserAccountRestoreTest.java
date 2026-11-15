package com.dispatchflow.access.domain.account;

import static com.dispatchflow.access.domain.account.AccountBuilder.ACCOUNT;
import static com.dispatchflow.access.domain.account.AccountBuilder.EMAIL;
import static com.dispatchflow.access.domain.account.AccountBuilder.NEW_EMAIL;
import static com.dispatchflow.access.domain.account.AccountBuilder.PASSWORD;
import static com.dispatchflow.access.domain.account.AccountBuilder.SENT;
import static com.dispatchflow.access.domain.account.AccountBuilder.anAccount;
import static com.dispatchflow.access.domain.account.AccountBuilder.anAnonymizedAccount;
import static com.dispatchflow.access.domain.account.AccountBuilder.link;
import static com.dispatchflow.access.domain.account.AccountBuilder.linkId;
import static com.dispatchflow.access.domain.account.AccountBuilder.openLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link UserAccount#restore} rebuilds every stored state and refuses each broken invariant at rest. */
class UserAccountRestoreTest {

    private static final Instant DEACTIVATED_AT = Instant.parse("2026-01-10T08:00:00Z");
    private static final Instant ANONYMIZED_AT = Instant.parse("2027-01-10T08:00:00Z");

    static Stream<AccountBuilder> everyBuilderState() {
        return Stream.of(
                anAccount(),
                anAccount().withRoles(),
                anAccount().withRoles(Role.ADMIN, Role.DISPATCHER, Role.DRIVER),
                anAccount().withPassword(Optional.empty()),
                anAccount().withTermsAcceptedAt(SENT),
                anAccount().withEmailSetByAnotherAt(SENT),
                anAccount()
                        .withLink(openLink("1", new LinkPurpose.SetPassword(), "a"))
                        .withLink(openLink("2", new LinkPurpose.Recovery(), "b"))
                        .withLink(openLink("3", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), "c"))
                        .withLink(link("4", new LinkPurpose.Recovery(), "d", LinkStatus.VOIDED))
                        .withLink(link("5", new LinkPurpose.SetPassword(), "e", LinkStatus.SPENT)),
                anAccount().withState(new AccountState.Deactivated(DEACTIVATED_AT)),
                anAnonymizedAccount(DEACTIVATED_AT, ANONYMIZED_AT));
    }

    @ParameterizedTest
    @MethodSource("everyBuilderState")
    void GivenAStoredAccount_WhenRestoredFromItsOwnSnapshot_ThenTheSnapshotIsTheSame(AccountBuilder builder) {
        UserAccount account = builder.build();

        assertThat(UserAccount.restore(account.snapshot()).snapshot()).isEqualTo(builder.snapshot());
        assertThat(account.version()).isEqualTo(AccountBuilder.VERSION);
        assertThat(account.pendingEvents()).isEmpty();
    }

    private static void assertStoredStateInvalid(AccountBuilder builder, String detail) {
        UserAccountSnapshot stored = builder.snapshot();

        assertThatThrownBy(() -> UserAccount.restore(stored))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("UserAccount");
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    @Test
    void GivenAnActiveAccountWithNoEmail_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAccount().withEmail(Optional.empty()),
                "an anonymized account holds no email, and every other account holds one");
    }

    @Test
    void GivenAnAnonymizedAccountStillHoldingItsEmail_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAnonymizedAccount(DEACTIVATED_AT, ANONYMIZED_AT).withEmail(Optional.of(EMAIL)),
                "an anonymized account holds no email, and every other account holds one");
    }

    @Test
    void GivenAnAnonymizedAccountStillHoldingAPassword_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAnonymizedAccount(DEACTIVATED_AT, ANONYMIZED_AT).withPassword(Optional.of(PASSWORD)),
                "an anonymized account holds no password and no link");
    }

    @Test
    void GivenAnAnonymizedAccountStillHoldingALink_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAnonymizedAccount(DEACTIVATED_AT, ANONYMIZED_AT)
                        .withLink(link("1", new LinkPurpose.Recovery(), "a", LinkStatus.VOIDED)),
                "an anonymized account holds no password and no link");
    }

    @Test
    void GivenAnAdminWhoseEmailAnotherAccountSet_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAccount().withRoles(Role.ADMIN).withEmailSetByAnotherAt(SENT),
                "an email another account set belongs to an account that holds it and no Admin role");
    }

    @Test
    void GivenAnAnonymizedAccountStillRecordingWhoSetItsEmail_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAnonymizedAccount(DEACTIVATED_AT, ANONYMIZED_AT).withEmailSetByAnotherAt(SENT),
                "an email another account set belongs to an account that holds it and no Admin role");
    }

    @Test
    void GivenAnAccountAnonymizedBeforeItWasDeactivated_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAnonymizedAccount(ANONYMIZED_AT, DEACTIVATED_AT), "an account is anonymized after it is deactivated");
    }

    @Test
    void GivenTwoLinksWithOneId_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAccount()
                        .withLink(link("1", new LinkPurpose.Recovery(), "a", LinkStatus.VOIDED))
                        .withLink(openLink("1", new LinkPurpose.SetPassword(), "b")),
                "two links share an ID");
    }

    @Test
    void GivenTwoLinksWithOneTokenHash_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAccount()
                        .withLink(link("1", new LinkPurpose.Recovery(), "same", LinkStatus.VOIDED))
                        .withLink(openLink("2", new LinkPurpose.SetPassword(), "same")),
                "two links share a token hash");
    }

    @Test
    void GivenTwoOpenLinksOfOneKind_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAccount()
                        .withLink(openLink("1", new LinkPurpose.EmailChange(EMAIL, ACCOUNT), "a"))
                        .withLink(openLink("2", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), "b")),
                "a newer link voids older open links of its kind");
    }

    @Test
    void GivenALinkThatExpiresWhenItIsSent_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAccount()
                        .withLink(new CredentialLinkSnapshot(
                                linkId("1"),
                                new LinkPurpose.Recovery(),
                                new TokenHash("a"),
                                SENT,
                                SENT,
                                LinkStatus.OPEN)),
                "a link expires after it is sent");
    }
}
