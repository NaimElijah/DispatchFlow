package com.dispatchflow.access.domain.session;

import static com.dispatchflow.access.domain.session.SessionBuilder.ACCOUNT_GENERATION;
import static com.dispatchflow.access.domain.session.SessionBuilder.FIRST_HASH;
import static com.dispatchflow.access.domain.session.SessionBuilder.OPENED;
import static com.dispatchflow.access.domain.session.SessionBuilder.SESSION_GENERATION;
import static com.dispatchflow.access.domain.session.SessionBuilder.aSession;
import static com.dispatchflow.access.domain.session.SessionBuilder.token;
import static com.dispatchflow.access.domain.session.SessionBuilder.tokenId;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.access.domain.AccessError.SessionRevoked;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.shared.DomainError;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every session state against every command (DL §14). A command is taken, with the result it
 * gives; refused by a named error that changes nothing; or, for {@code exchange}, answered with a
 * {@link RefreshOutcome}.
 */
class SessionLifecycleTest {

    private static final Instant NOW = OPENED.plus(Duration.ofMinutes(3));
    private static final Instant CONTACT_EXPIRED =
            OPENED.plus(Session.CONTACT_RETENTION).plusSeconds(1);
    private static final TokenHash SECOND_HASH = new TokenHash("second");

    private enum State {
        LIVE,
        REVOKED
    }

    /** What a command is expected to do: be taken with its result, or be refused. */
    private sealed interface Expected {}

    /**
     * The command is taken; the check gets the snapshot before, the session after and the answer.
     *
     * @param check the result's checks
     */
    private record Taken(Check check) implements Expected {}

    private record Refused(DomainError error) implements Expected {}

    @FunctionalInterface
    private interface Check {
        void verify(SessionSnapshot before, Session after, Object answer);
    }

    private record Command(String name, Function<Session, Object> call) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final Command EXCHANGE = new Command(
            "exchange", session -> session.exchange(FIRST_HASH, ACCOUNT_GENERATION, tokenId("2"), SECOND_HASH, NOW));
    private static final Command CONFIRM_PASSWORD = new Command("confirmPassword", session -> {
        session.confirmPassword(ACCOUNT_GENERATION, tokenId("2"), SECOND_HASH, NOW);
        return session;
    });
    private static final Command REQUIRE_FRESH_STEP_UP = new Command("requireFreshStepUp", session -> {
        session.requireFreshStepUp(ACCOUNT_GENERATION, NOW);
        return session;
    });
    private static final Command RECORD_CONTACT = new Command("recordContact", session -> {
        session.recordContact(NOW);
        return session;
    });
    private static final Command DROP_OLD_CONTACTS = new Command("dropOldContacts", session -> {
        session.dropOldContacts(CONTACT_EXPIRED);
        return session;
    });
    private static final Command REVOKE = new Command("revoke", session -> {
        session.revoke(NOW);
        return session;
    });
    private static final Command REVOKE_WITH_ACCOUNT = new Command("revokeWithAccount", session -> {
        session.revokeWithAccount(ACCOUNT_GENERATION.next(), NOW);
        return session;
    });

    private static Expected taken(BiConsumer<SessionSnapshot, Session> check) {
        return new Taken((before, after, answer) -> check.accept(before, after));
    }

    /** A session with one contact time, when it opened. */
    private static Session in(State state) {
        return switch (state) {
            case LIVE -> aSession().withContactTimes(OPENED).build();
            case REVOKED ->
                aSession()
                        .withState(new SessionState.Revoked(OPENED))
                        .withContactTimes(OPENED)
                        .build();
        };
    }

    static Stream<Arguments> stateAndCommand() {
        Expected revoked = new Refused(new SessionRevoked());
        Expected unchanged =
                taken((before, after) -> assertThat(after.snapshot()).isEqualTo(before));
        Expected contactsDropped = taken(
                (before, after) -> assertThat(after.snapshot().contactTimes()).isEmpty());
        return Stream.of(
                Arguments.of(State.LIVE, EXCHANGE, new Taken((before, after, answer) -> {
                    assertThat(answer).isEqualTo(new RefreshOutcome.Issued(tokenId("2")));
                    assertThat(after.snapshot().tokens())
                            .containsExactly(
                                    token("1", "first", OPENED, new TokenState.Exchanged(NOW, tokenId("2"))),
                                    token("2", "second", NOW, new TokenState.Current()));
                })),
                Arguments.of(State.LIVE, CONFIRM_PASSWORD, taken((before, after) -> {
                    assertThat(after.generation()).isEqualTo(SESSION_GENERATION.next());
                    assertThat(after.snapshot().lastPasswordAt()).isEqualTo(NOW);
                    assertThat(after.snapshot().tokens())
                            .containsExactly(
                                    token("1", "first", OPENED, new TokenState.Superseded(NOW)),
                                    token("2", "second", NOW, new TokenState.Current()));
                })),
                Arguments.of(State.LIVE, REQUIRE_FRESH_STEP_UP, unchanged),
                Arguments.of(
                        State.LIVE,
                        RECORD_CONTACT,
                        taken((before, after) ->
                                assertThat(after.snapshot().contactTimes()).containsExactly(OPENED, NOW))),
                Arguments.of(State.LIVE, DROP_OLD_CONTACTS, contactsDropped),
                Arguments.of(State.LIVE, REVOKE, taken((before, after) -> {
                    assertThat(after.state()).isEqualTo(new SessionState.Revoked(NOW));
                    assertThat(after.snapshot().tokens()).isEqualTo(before.tokens());
                })),
                Arguments.of(State.LIVE, REVOKE_WITH_ACCOUNT, taken((before, after) -> {
                    assertThat(after.state()).isEqualTo(new SessionState.Revoked(NOW));
                    assertThat(after.snapshot().tokens()).isEqualTo(before.tokens());
                })),
                Arguments.of(State.REVOKED, EXCHANGE, new Taken((before, after, answer) -> {
                    assertThat(answer).isEqualTo(RefreshOutcome.refused());
                    assertThat(after.snapshot()).isEqualTo(before);
                })),
                Arguments.of(State.REVOKED, CONFIRM_PASSWORD, revoked),
                Arguments.of(State.REVOKED, REQUIRE_FRESH_STEP_UP, revoked),
                Arguments.of(State.REVOKED, RECORD_CONTACT, revoked),
                Arguments.of(State.REVOKED, DROP_OLD_CONTACTS, contactsDropped),
                Arguments.of(State.REVOKED, REVOKE, unchanged),
                Arguments.of(State.REVOKED, REVOKE_WITH_ACCOUNT, unchanged));
    }

    @ParameterizedTest(name = "{0} × {1}")
    @MethodSource("stateAndCommand")
    void GivenASessionInEachState_WhenEachCommandIsCalled_ThenItIsTakenOrRefused(
            State state, Command command, Expected expected) {
        Session session = in(state);
        SessionSnapshot before = session.snapshot();

        switch (expected) {
            case Taken taken ->
                taken.check().verify(before, session, command.call().apply(session));
            case Refused refused -> {
                assertRefused(() -> command.call().apply(session), refused.error());
                assertThat(session.snapshot()).isEqualTo(before);
            }
        }
        assertThat(session.version()).isEqualTo(SessionBuilder.VERSION);
        assertThat(session.pendingEvents()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("everyCommand")
    void GivenEveryCommand_WhenTheMatrixIsListed_ThenEachStateCoversIt(Command command) {
        assertThat(stateAndCommand().filter(row -> row.get()[1] == command).map(row -> row.get()[0]))
                .containsExactlyInAnyOrder((Object[]) State.values());
    }

    static Stream<Command> everyCommand() {
        return Stream.of(
                EXCHANGE,
                CONFIRM_PASSWORD,
                REQUIRE_FRESH_STEP_UP,
                RECORD_CONTACT,
                DROP_OLD_CONTACTS,
                REVOKE,
                REVOKE_WITH_ACCOUNT);
    }

    /** A query takes nothing and returns a value; every other public method is a command. */
    @Test
    void GivenTheSessionsPublicMethods_WhenTheCommandsAreListed_ThenTheMatrixListsEveryCommand() {
        List<String> commands = Arrays.stream(Session.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        && !Modifier.isStatic(method.getModifiers())
                        && !method.isSynthetic())
                .filter(method -> method.getParameterCount() > 0 || method.getReturnType() == void.class)
                .map(Method::getName)
                .toList();

        assertThat(everyCommand().map(Command::name)).containsExactlyInAnyOrderElementsOf(commands);
    }
}
