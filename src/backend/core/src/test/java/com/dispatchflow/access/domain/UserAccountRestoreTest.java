package com.dispatchflow.access.domain;

import static com.dispatchflow.access.domain.AccountBuilder.EMAIL;
import static com.dispatchflow.access.domain.AccountBuilder.NEW_EMAIL;
import static com.dispatchflow.access.domain.AccountBuilder.PASSWORD;
import static com.dispatchflow.access.domain.AccountBuilder.SENT;
import static com.dispatchflow.access.domain.AccountBuilder.anAccount;
import static com.dispatchflow.access.domain.AccountBuilder.anAnonymizedAccount;
import static com.dispatchflow.access.domain.AccountBuilder.link;
import static com.dispatchflow.access.domain.AccountBuilder.linkId;
import static com.dispatchflow.access.domain.AccountBuilder.openLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                anAccount()
                        .withLink(openLink("1", new LinkPurpose.SetPassword(), "a"))
                        .withLink(openLink("2", new LinkPurpose.Recovery(), "b"))
                        .withLink(openLink("3", new LinkPurpose.EmailChange(NEW_EMAIL), "c"))
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

    private static void assertStoredStateInvalid(AccountBuilder builder) {
        UserAccountSnapshot stored = builder.snapshot();

        assertThatThrownBy(() -> UserAccount.restore(stored))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("UserAccount"));
    }

    @Test
    void GivenAnActiveAccountWithNoEmail_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(anAccount().withEmail(Optional.empty()));
    }

    @Test
    void GivenAnAnonymizedAccountStillHoldingItsEmail_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAnonymizedAccount(DEACTIVATED_AT, ANONYMIZED_AT).withEmail(Optional.of(EMAIL)));
    }

    @Test
    void GivenAnAnonymizedAccountStillHoldingAPassword_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(
                anAnonymizedAccount(DEACTIVATED_AT, ANONYMIZED_AT).withPassword(Optional.of(PASSWORD)));
    }

    @Test
    void GivenAnAnonymizedAccountStillHoldingALink_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(anAnonymizedAccount(DEACTIVATED_AT, ANONYMIZED_AT)
                .withLink(link("1", new LinkPurpose.Recovery(), "a", LinkStatus.VOIDED)));
    }

    @Test
    void GivenAnAccountAnonymizedBeforeItWasDeactivated_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(anAnonymizedAccount(ANONYMIZED_AT, DEACTIVATED_AT));
    }

    @Test
    void GivenTwoLinksWithOneId_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(anAccount()
                .withLink(link("1", new LinkPurpose.Recovery(), "a", LinkStatus.VOIDED))
                .withLink(openLink("1", new LinkPurpose.SetPassword(), "b")));
    }

    @Test
    void GivenTwoLinksWithOneTokenHash_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(anAccount()
                .withLink(link("1", new LinkPurpose.Recovery(), "same", LinkStatus.VOIDED))
                .withLink(openLink("2", new LinkPurpose.SetPassword(), "same")));
    }

    @Test
    void GivenTwoOpenLinksOfOneKind_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(anAccount()
                .withLink(openLink("1", new LinkPurpose.EmailChange(EMAIL), "a"))
                .withLink(openLink("2", new LinkPurpose.EmailChange(NEW_EMAIL), "b")));
    }

    @Test
    void GivenALinkThatExpiresWhenItIsSent_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(anAccount()
                .withLink(new CredentialLinkSnapshot(
                        linkId("1"), new LinkPurpose.Recovery(), new TokenHash("a"), SENT, SENT, LinkStatus.OPEN)));
    }
}
