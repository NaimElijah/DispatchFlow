package com.dispatchflow.access.domain;

import static com.dispatchflow.access.domain.SessionBuilder.OPENED;
import static com.dispatchflow.access.domain.SessionBuilder.aSession;
import static com.dispatchflow.access.domain.SessionBuilder.token;
import static com.dispatchflow.access.domain.SessionBuilder.tokenId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link Session#restore} rebuilds every stored state and refuses each broken invariant at rest. */
class SessionRestoreTest {

    private static final Instant LATER = OPENED.plus(Duration.ofMinutes(20));
    private static final Instant LATEST = OPENED.plus(Duration.ofMinutes(40));

    static Stream<SessionBuilder> everyBuilderState() {
        return Stream.of(
                aSession(),
                aSession().withState(new SessionState.Revoked(LATER)),
                aSession().withContactTimes(OPENED, LATER, LATEST),
                aSession().withLastPasswordAt(LATER),
                aSession()
                        .withTokens(
                                token("1", "a", OPENED, new TokenState.Exchanged(LATER, tokenId("3"))),
                                token("2", "b", LATER, new TokenState.Replaced(LATER.plusSeconds(5))),
                                token("3", "c", LATER.plusSeconds(5), new TokenState.Superseded(LATEST)),
                                token("4", "d", LATEST, new TokenState.Current())),
                aSession()
                        .withState(new SessionState.Revoked(LATEST))
                        .withTokens(
                                token("1", "a", OPENED, new TokenState.Exchanged(LATER, tokenId("2"))),
                                token("2", "b", LATER, new TokenState.Replaced(LATER.plusSeconds(5)))));
    }

    @ParameterizedTest
    @MethodSource("everyBuilderState")
    void GivenAStoredSession_WhenRestoredFromItsOwnSnapshot_ThenTheSnapshotIsTheSame(SessionBuilder builder) {
        Session session = builder.build();

        assertThat(Session.restore(session.snapshot()).snapshot()).isEqualTo(builder.snapshot());
        assertThat(session.version()).isEqualTo(SessionBuilder.VERSION);
        assertThat(session.pendingEvents()).isEmpty();
    }

    private static void assertStoredStateInvalid(SessionBuilder builder) {
        SessionSnapshot stored = builder.snapshot();

        assertThatThrownBy(() -> Session.restore(stored))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("Session"));
    }

    @Test
    void GivenASessionWithNoToken_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(aSession().withTokens());
    }

    @Test
    void GivenTwoTokensWithOneId_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(aSession()
                .withTokens(
                        token("1", "a", OPENED, new TokenState.Superseded(LATER)),
                        token("1", "b", LATER, new TokenState.Current())));
    }

    @Test
    void GivenTwoTokensWithOneHash_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(aSession()
                .withTokens(
                        token("1", "same", OPENED, new TokenState.Superseded(LATER)),
                        token("2", "same", LATER, new TokenState.Current())));
    }

    @Test
    void GivenALiveSessionWithNoCurrentToken_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(aSession().withTokens(token("1", "a", OPENED, new TokenState.Superseded(LATER))));
    }

    @Test
    void GivenALiveSessionWithTwoCurrentTokens_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(aSession()
                .withTokens(
                        token("1", "a", OPENED, new TokenState.Current()),
                        token("2", "b", LATER, new TokenState.Current())));
    }

    @Test
    void GivenARevokedSessionWithTwoCurrentTokens_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(aSession()
                .withState(new SessionState.Revoked(LATEST))
                .withTokens(
                        token("1", "a", OPENED, new TokenState.Current()),
                        token("2", "b", LATER, new TokenState.Current())));
    }

    @Test
    void GivenAnExchangedTokenWhoseSuccessorIsNotInTheChain_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(aSession()
                .withTokens(
                        token("1", "a", OPENED, new TokenState.Exchanged(LATER, tokenId("9"))),
                        token("2", "b", LATER, new TokenState.Current())));
    }

    @Test
    void GivenAnExchangedTokenThatIsItsOwnSuccessor_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(aSession()
                .withTokens(
                        token("1", "a", OPENED, new TokenState.Exchanged(LATER, tokenId("1"))),
                        token("2", "b", LATER, new TokenState.Current())));
    }

    @Test
    void GivenContactTimesOutOfOrder_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(aSession().withContactTimes(LATER, OPENED));
    }

    @Test
    void GivenARepeatedContactTime_WhenRestored_ThenStoredStateInvalid() {
        assertStoredStateInvalid(aSession().withContactTimes(LATER, LATER));
    }
}
