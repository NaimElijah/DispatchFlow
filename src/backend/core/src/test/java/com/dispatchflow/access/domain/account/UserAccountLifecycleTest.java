package com.dispatchflow.access.domain.account;

import static com.dispatchflow.access.domain.account.AccountBuilder.ACCOUNT;
import static com.dispatchflow.access.domain.account.AccountBuilder.NEW_EMAIL;
import static com.dispatchflow.access.domain.account.AccountBuilder.OTHER_ADMIN;
import static com.dispatchflow.access.domain.account.AccountBuilder.SENT;
import static com.dispatchflow.access.domain.account.AccountBuilder.THIRD_ADMIN;
import static com.dispatchflow.access.domain.account.AccountBuilder.VERSION;
import static com.dispatchflow.access.domain.account.AccountBuilder.anAccount;
import static com.dispatchflow.access.domain.account.AccountBuilder.anAnonymizedAccount;
import static com.dispatchflow.access.domain.account.AccountBuilder.linkId;
import static com.dispatchflow.access.domain.account.AccountBuilder.openLink;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.access.domain.AccessError.AccountAnonymized;
import com.dispatchflow.access.domain.AccessError.AccountDeactivated;
import com.dispatchflow.access.domain.AccessError.AccountNotDeactivated;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.events.SessionsRevoked;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.EventId;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every account state against every command (DL §14): each either takes the command, with the
 * result and events that command gives, or refuses it with the named error and changes nothing.
 */
class UserAccountLifecycleTest {

    private static final Instant DEACTIVATED_AT = Instant.parse("2025-06-15T06:00:00Z");
    private static final Instant ANONYMIZED_AT = Instant.parse("2026-06-15T06:00:00Z");
    private static final Instant NOW = SENT.plus(Duration.ofMinutes(10));
    private static final ActiveAdmins THREE_ADMINS =
            ActiveAdmins.readUnderLock(List.of(ACCOUNT, OTHER_ADMIN, THIRD_ADMIN));
    private static final TokenHash SET_PASSWORD_TOKEN = new TokenHash("set-password-token");
    private static final TokenHash EMAIL_TOKEN = new TokenHash("email-token");
    private static final PasswordHash NEW_HASH = new PasswordHash("new-hash");

    private enum State {
        ACTIVE,
        DEACTIVATED,
        ANONYMIZED
    }

    /**
     * A command, and what an accepting state must show after it, given the snapshot before.
     *
     * @param name the method's name, for the test's display
     * @param call the call
     * @param accepted the checks of an accepted call
     */
    private record Command(
            String name, Consumer<UserAccount> call, BiConsumer<UserAccountSnapshot, UserAccount> accepted) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static void revokedOnce(UserAccountSnapshot before, UserAccount after) {
        assertThat(after.generation()).isEqualTo(before.generation().next());
        assertThat(after.pendingEvents())
                .containsExactly(new SessionsRevoked(
                        new EventId("SessionsRevoked", ACCOUNT.value(), VERSION.next(), 0),
                        NOW,
                        ACCOUNT,
                        before.generation().next().value()));
    }

    private static void nothingRaised(UserAccountSnapshot before, UserAccount after) {
        assertThat(after.generation()).isEqualTo(before.generation());
        assertThat(after.pendingEvents()).isEmpty();
    }

    private static void linksVoided(UserAccount after) {
        assertThat(after.snapshot().links())
                .extracting(CredentialLinkSnapshot::status)
                .containsOnly(LinkStatus.VOIDED);
    }

    private static final Command SIGN_IN =
            new Command("checkSignInAllowed", UserAccount::checkSignInAllowed, (before, after) -> {
                assertThat(after.snapshot()).isEqualTo(before);
                nothingRaised(before, after);
            });
    private static final Command CHANGE_ROLES = new Command(
            "changeRoles",
            account -> account.changeRoles(RoleSet.of(Role.DISPATCHER), THREE_ADMINS, OTHER_ADMIN, NOW),
            (before, after) -> {
                assertThat(after.roles()).isEqualTo(RoleSet.of(Role.DISPATCHER));
                revokedOnce(before, after);
            });
    private static final Command REMOVE_DRIVER_ROLE =
            new Command("removeDriverRole", account -> account.removeDriverRole(NOW), (before, after) -> {
                assertThat(after.roles()).isEqualTo(RoleSet.of(Role.ADMIN));
                assertThat(after.state()).isEqualTo(before.state());
                revokedOnce(before, after);
            });
    private static final Command DEACTIVATE =
            new Command("deactivate", account -> account.deactivate(THREE_ADMINS, NOW), (before, after) -> {
                assertThat(after.state()).isEqualTo(new AccountState.Deactivated(NOW));
                linksVoided(after);
                revokedOnce(before, after);
            });
    private static final Command DEACTIVATE_ON_REQUEST = new Command(
            "deactivateOnWrittenRequest", account -> account.deactivateOnWrittenRequest(NOW), (before, after) -> {
                assertThat(after.state()).isEqualTo(new AccountState.Deactivated(NOW));
                linksVoided(after);
                revokedOnce(before, after);
            });
    private static final Command REACTIVATE = new Command("reactivate", UserAccount::reactivate, (before, after) -> {
        assertThat(after.state()).isEqualTo(new AccountState.Active());
        assertThat(after.snapshot().links()).isEqualTo(before.links());
        nothingRaised(before, after);
    });
    private static final Command END_EVERY_SESSION =
            new Command("endEverySession", account -> account.endEverySession(NOW), (before, after) -> {
                assertThat(after.state()).isEqualTo(before.state());
                linksVoided(after);
                revokedOnce(before, after);
            });
    private static final Command ISSUE_LINK = new Command(
            "issueLink",
            account -> account.issueLink(new LinkPurpose.Recovery(), linkId("9"), new TokenHash("fresh"), NOW),
            (before, after) -> {
                List<CredentialLinkSnapshot> links = after.snapshot().links();
                assertThat(links).hasSize(before.links().size() + 1);
                assertThat(links.get(links.size() - 1).status()).isEqualTo(LinkStatus.OPEN);
                nothingRaised(before, after);
            });
    private static final Command SET_PASSWORD_BY_LINK = new Command(
            "setPasswordByLink",
            account -> account.setPasswordByLink(SET_PASSWORD_TOKEN, NEW_HASH, NOW),
            (before, after) -> {
                assertThat(after.passwordHash()).contains(NEW_HASH);
                assertThat(after.snapshot().links())
                        .extracting(CredentialLinkSnapshot::status)
                        .containsExactly(LinkStatus.SPENT, LinkStatus.VOIDED);
                revokedOnce(before, after);
            });
    private static final Command CONFIRM_EMAIL_BY_LINK = new Command(
            "confirmEmailByLink",
            account -> account.confirmEmailByLink(EMAIL_TOKEN, EmailHolder.nobody(NEW_EMAIL), NOW),
            (before, after) -> {
                assertThat(after.email()).contains(NEW_EMAIL);
                assertThat(after.snapshot().links())
                        .extracting(CredentialLinkSnapshot::status)
                        .containsExactly(LinkStatus.VOIDED, LinkStatus.SPENT);
                revokedOnce(before, after);
            });
    private static final Command CHANGE_PASSWORD =
            new Command("changePassword", account -> account.changePassword(NEW_HASH, NOW), (before, after) -> {
                assertThat(after.passwordHash()).contains(NEW_HASH);
                linksVoided(after);
                revokedOnce(before, after);
            });
    private static final Command CHANGE_LANGUAGE =
            new Command("changeLanguage", account -> account.changeLanguage(Language.ENGLISH), (before, after) -> {
                assertThat(after.language()).isEqualTo(Language.ENGLISH);
                nothingRaised(before, after);
            });
    private static final Command ACCEPT_TERMS =
            new Command("acceptTerms", account -> account.acceptTerms(NOW), (before, after) -> {
                assertThat(after.snapshot().termsAcceptedAt()).contains(NOW);
                nothingRaised(before, after);
            });
    private static final Command ANONYMIZE =
            new Command("anonymize", account -> account.anonymize(NOW), (before, after) -> {
                assertThat(after.state()).isEqualTo(new AccountState.Anonymized(DEACTIVATED_AT, NOW));
                assertThat(after.email()).isEmpty();
                assertThat(after.snapshot().links()).isEmpty();
                assertThat(after.pendingEvents())
                        .singleElement()
                        .isExactlyInstanceOf(com.dispatchflow.access.events.AccountAnonymized.class);
            });

    /** An Admin and Driver, with an open set-password link and an open email-change link. */
    private static UserAccount in(State state) {
        AccountBuilder builder = switch (state) {
            case ACTIVE -> anAccount();
            case DEACTIVATED -> anAccount().withState(new AccountState.Deactivated(DEACTIVATED_AT));
            case ANONYMIZED -> anAnonymizedAccount(DEACTIVATED_AT, ANONYMIZED_AT);
        };
        builder.withRoles(Role.ADMIN, Role.DRIVER);
        if (state != State.ANONYMIZED) {
            builder.withLink(openLink("1", new LinkPurpose.SetPassword(), SET_PASSWORD_TOKEN.value()))
                    .withLink(openLink("2", new LinkPurpose.EmailChange(NEW_EMAIL, ACCOUNT), EMAIL_TOKEN.value()));
        }
        return builder.build();
    }

    static Stream<Arguments> stateAndCommand() {
        DomainError deactivated = new AccountDeactivated();
        DomainError anonymized = new AccountAnonymized();
        DomainError notDeactivated = new AccountNotDeactivated();
        return Stream.of(
                Arguments.of(State.ACTIVE, SIGN_IN, null),
                Arguments.of(State.ACTIVE, CHANGE_ROLES, null),
                Arguments.of(State.ACTIVE, REMOVE_DRIVER_ROLE, null),
                Arguments.of(State.ACTIVE, DEACTIVATE, null),
                Arguments.of(State.ACTIVE, DEACTIVATE_ON_REQUEST, null),
                Arguments.of(State.ACTIVE, REACTIVATE, notDeactivated),
                Arguments.of(State.ACTIVE, END_EVERY_SESSION, null),
                Arguments.of(State.ACTIVE, ISSUE_LINK, null),
                Arguments.of(State.ACTIVE, SET_PASSWORD_BY_LINK, null),
                Arguments.of(State.ACTIVE, CONFIRM_EMAIL_BY_LINK, null),
                Arguments.of(State.ACTIVE, CHANGE_PASSWORD, null),
                Arguments.of(State.ACTIVE, CHANGE_LANGUAGE, null),
                Arguments.of(State.ACTIVE, ACCEPT_TERMS, null),
                Arguments.of(State.ACTIVE, ANONYMIZE, notDeactivated),
                Arguments.of(State.DEACTIVATED, SIGN_IN, deactivated),
                Arguments.of(State.DEACTIVATED, CHANGE_ROLES, deactivated),
                Arguments.of(State.DEACTIVATED, REMOVE_DRIVER_ROLE, null),
                Arguments.of(State.DEACTIVATED, DEACTIVATE, deactivated),
                Arguments.of(State.DEACTIVATED, DEACTIVATE_ON_REQUEST, deactivated),
                Arguments.of(State.DEACTIVATED, REACTIVATE, null),
                Arguments.of(State.DEACTIVATED, END_EVERY_SESSION, deactivated),
                Arguments.of(State.DEACTIVATED, ISSUE_LINK, deactivated),
                Arguments.of(State.DEACTIVATED, SET_PASSWORD_BY_LINK, deactivated),
                Arguments.of(State.DEACTIVATED, CONFIRM_EMAIL_BY_LINK, deactivated),
                Arguments.of(State.DEACTIVATED, CHANGE_PASSWORD, deactivated),
                Arguments.of(State.DEACTIVATED, CHANGE_LANGUAGE, deactivated),
                Arguments.of(State.DEACTIVATED, ACCEPT_TERMS, deactivated),
                Arguments.of(State.DEACTIVATED, ANONYMIZE, null),
                Arguments.of(State.ANONYMIZED, SIGN_IN, anonymized),
                Arguments.of(State.ANONYMIZED, CHANGE_ROLES, anonymized),
                Arguments.of(State.ANONYMIZED, REMOVE_DRIVER_ROLE, null),
                Arguments.of(State.ANONYMIZED, DEACTIVATE, anonymized),
                Arguments.of(State.ANONYMIZED, DEACTIVATE_ON_REQUEST, anonymized),
                Arguments.of(State.ANONYMIZED, REACTIVATE, anonymized),
                Arguments.of(State.ANONYMIZED, END_EVERY_SESSION, anonymized),
                Arguments.of(State.ANONYMIZED, ISSUE_LINK, anonymized),
                Arguments.of(State.ANONYMIZED, SET_PASSWORD_BY_LINK, anonymized),
                Arguments.of(State.ANONYMIZED, CONFIRM_EMAIL_BY_LINK, anonymized),
                Arguments.of(State.ANONYMIZED, CHANGE_PASSWORD, anonymized),
                Arguments.of(State.ANONYMIZED, CHANGE_LANGUAGE, anonymized),
                Arguments.of(State.ANONYMIZED, ACCEPT_TERMS, anonymized),
                Arguments.of(State.ANONYMIZED, ANONYMIZE, anonymized));
    }

    @ParameterizedTest(name = "{0} × {1} → {2}")
    @MethodSource("stateAndCommand")
    void GivenAnAccountInEachState_WhenEachCommandIsCalled_ThenItIsTakenOrRefusedByName(
            State state, Command command, @Nullable DomainError refusal) {
        UserAccount account = in(state);
        UserAccountSnapshot before = account.snapshot();

        if (refusal == null) {
            command.call().accept(account);
            command.accepted().accept(before, account);
        } else {
            assertRefused(() -> command.call().accept(account), refusal);
            assertThat(account.snapshot()).isEqualTo(before);
            assertThat(account.pendingEvents()).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("everyCommand")
    void GivenEveryCommand_WhenTheMatrixIsListed_ThenEachStateCoversIt(Command command) {
        assertThat(stateAndCommand().filter(row -> row.get()[1] == command).map(row -> row.get()[0]))
                .containsExactlyInAnyOrder((Object[]) State.values());
    }

    /** A query takes nothing and returns a value; every other public method is a command. */
    @Test
    void GivenTheAccountsPublicMethods_WhenTheCommandsAreListed_ThenTheMatrixListsEveryCommand() {
        List<String> commands = Arrays.stream(UserAccount.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        && !Modifier.isStatic(method.getModifiers())
                        && !method.isSynthetic())
                .filter(method -> method.getParameterCount() > 0 || method.getReturnType() == void.class)
                .map(Method::getName)
                .toList();

        assertThat(everyCommand().map(Command::name)).containsExactlyInAnyOrderElementsOf(commands);
    }

    static Stream<Command> everyCommand() {
        return Stream.of(
                SIGN_IN,
                CHANGE_ROLES,
                REMOVE_DRIVER_ROLE,
                DEACTIVATE,
                DEACTIVATE_ON_REQUEST,
                REACTIVATE,
                END_EVERY_SESSION,
                ISSUE_LINK,
                SET_PASSWORD_BY_LINK,
                CONFIRM_EMAIL_BY_LINK,
                CHANGE_PASSWORD,
                CHANGE_LANGUAGE,
                ACCEPT_TERMS,
                ANONYMIZE);
    }
}
