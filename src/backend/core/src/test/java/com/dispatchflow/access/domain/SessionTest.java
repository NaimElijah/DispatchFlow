package com.dispatchflow.access.domain;

import static com.dispatchflow.access.domain.SessionBuilder.ACCOUNT_GENERATION;
import static com.dispatchflow.access.domain.SessionBuilder.FIRST;
import static com.dispatchflow.access.domain.SessionBuilder.FIRST_HASH;
import static com.dispatchflow.access.domain.SessionBuilder.OPENED;
import static com.dispatchflow.access.domain.SessionBuilder.SESSION;
import static com.dispatchflow.access.domain.SessionBuilder.SESSION_GENERATION;
import static com.dispatchflow.access.domain.SessionBuilder.VERSION;
import static com.dispatchflow.access.domain.SessionBuilder.aSession;
import static com.dispatchflow.access.domain.SessionBuilder.token;
import static com.dispatchflow.access.domain.SessionBuilder.tokenId;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.access.domain.AccessError.AccountDeactivated;
import com.dispatchflow.access.domain.AccessError.AccountHasNoRole;
import com.dispatchflow.access.domain.AccessError.SessionRevoked;
import com.dispatchflow.access.domain.AccessError.StepUpRequired;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static final RefreshTokenId SECOND = tokenId("2");
    private static final TokenHash SECOND_HASH = new TokenHash("second");
    private static final RefreshTokenId THIRD = tokenId("3");
    private static final TokenHash THIRD_HASH = new TokenHash("third");
    private static final RefreshTokenId FOURTH = tokenId("4");
    private static final TokenHash FOURTH_HASH = new TokenHash("fourth");
    private static final Instant EXCHANGED = OPENED.plus(Duration.ofMinutes(15));
    private static final Instant NOW = OPENED.plus(Duration.ofHours(2));

    /** Asserts the refusal through the shared helper, and that it changed nothing. */
    private static void assertRefusedUnchanged(Session session, ThrowingCallable call, DomainError expected) {
        SessionSnapshot before = session.snapshot();

        assertRefused(call, expected);

        assertThat(session.snapshot()).isEqualTo(before);
        assertThat(session.pendingEvents()).isEmpty();
    }

    private static void assertRefusedUnchanged(Session session, TokenHash presented, Instant at) {
        SessionSnapshot before = session.snapshot();

        RefreshOutcome outcome = session.exchange(presented, ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, at);

        assertThat(outcome).isEqualTo(RefreshOutcome.refused());
        assertThat(session.snapshot()).isEqualTo(before);
    }

    private static void assertRefusedAndRevoked(Session session, TokenHash presented, Instant at) {
        RefreshOutcome outcome = session.exchange(presented, ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, at);

        assertThat(outcome).isEqualTo(RefreshOutcome.refused());
        assertThat(session.state()).isEqualTo(new SessionState.Revoked(at));
        assertThat(session.snapshot().tokens()).noneMatch(stored -> stored.id().equals(FOURTH));
    }

    /** A session whose first token was exchanged at {@link #EXCHANGED} for the second, still current. */
    private static SessionBuilder exchangedOnce() {
        return aSession()
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Exchanged(EXCHANGED, SECOND)),
                        token("2", "second", EXCHANGED, new TokenState.Current()));
    }

    @Test
    void GivenASessionWithAChainOfTokens_WhenItsSnapshotIsPrinted_ThenNoTokenHashShows() {
        Session session = exchangedOnce().build();

        assertThat(session.snapshot().toString()).doesNotContain(FIRST_HASH.value(), SECOND_HASH.value());
    }

    // Opening

    @Test
    void GivenAnAccountThatMaySignIn_WhenASessionOpens_ThenItIsLiveUnderTheAccountsGenerationWithOneCurrentToken() {
        UserAccount account = AccountBuilder.anAccount().build();

        Session session = Session.open(SESSION, account, FIRST, FIRST_HASH, OPENED);

        assertThat(session.version()).isEqualTo(Version.NEW);
        assertThat(session.snapshot())
                .isEqualTo(new SessionSnapshot(
                        Version.NEW,
                        SESSION,
                        AccountBuilder.ACCOUNT,
                        AccountBuilder.GENERATION,
                        SessionGeneration.INITIAL,
                        new SessionState.Live(),
                        OPENED,
                        OPENED,
                        List.of(),
                        List.of(new RefreshTokenSnapshot(FIRST, FIRST_HASH, OPENED, new TokenState.Current()))));
        assertThat(session.pendingEvents()).isEmpty();
    }

    @Test
    void GivenADeactivatedAccount_WhenASessionOpens_ThenAccountDeactivated() {
        UserAccount account = AccountBuilder.anAccount()
                .withState(new AccountState.Deactivated(OPENED))
                .build();

        assertRefused(() -> Session.open(SESSION, account, FIRST, FIRST_HASH, OPENED), new AccountDeactivated());
    }

    @Test
    void GivenAnAccountWithNoRole_WhenASessionOpens_ThenAccountHasNoRole() {
        UserAccount account = AccountBuilder.anAccount().withRoles().build();

        assertRefused(() -> Session.open(SESSION, account, FIRST, FIRST_HASH, OPENED), new AccountHasNoRole());
    }

    // Exchanging a refresh token

    @Test
    void GivenTheCurrentToken_WhenExchanged_ThenTheDeviceGetsTheNextTokenAndTheOldOneIsMarkedExchanged() {
        Session session = aSession().build();

        RefreshOutcome outcome = session.exchange(FIRST_HASH, ACCOUNT_GENERATION, SECOND, SECOND_HASH, EXCHANGED);

        assertThat(outcome).isEqualTo(new RefreshOutcome.Issued(SECOND));
        assertThat(session.snapshot().tokens())
                .containsExactly(
                        token("1", "first", OPENED, new TokenState.Exchanged(EXCHANGED, SECOND)),
                        token("2", "second", EXCHANGED, new TokenState.Current()));
        assertThat(session.state()).isEqualTo(new SessionState.Live());
        assertThat(session.pendingEvents()).isEmpty();
    }

    @Test
    void GivenTheCurrentTokenOneSecondBefore14Days_WhenExchanged_ThenItIsExchanged() {
        Session session = aSession().build();
        Instant justInTime = OPENED.plus(Duration.ofDays(14)).minusSeconds(1);

        assertThat(session.exchange(FIRST_HASH, ACCOUNT_GENERATION, SECOND, SECOND_HASH, justInTime))
                .isEqualTo(new RefreshOutcome.Issued(SECOND));
    }

    @Test
    void GivenTheCurrentToken14DaysAfterItsIssue_WhenExchanged_ThenRefreshRefused() {
        Session session = aSession().build();

        assertRefusedUnchanged(session, FIRST_HASH, OPENED.plus(Duration.ofDays(14)));
    }

    @Test
    void GivenALongLivedSession_WhenItsLatestTokenIsUnder14DaysOld_ThenItIsExchangedWithNoCapOnTheSession() {
        Instant aMonthLater = OPENED.plus(Duration.ofDays(30));
        Session session = aSession()
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Exchanged(aMonthLater, SECOND)),
                        token("2", "second", aMonthLater, new TokenState.Current()))
                .build();

        assertThat(session.exchange(
                        SECOND_HASH, ACCOUNT_GENERATION, THIRD, THIRD_HASH, aMonthLater.plus(Duration.ofDays(13))))
                .isEqualTo(new RefreshOutcome.Issued(THIRD));
    }

    @Test
    void GivenATokenOfAnotherSession_WhenExchanged_ThenRefreshRefused() {
        Session session = aSession().build();

        assertRefusedUnchanged(session, new TokenHash("not-ours"), EXCHANGED);
    }

    @Test
    void GivenARevokedSession_WhenItsCurrentTokenIsExchanged_ThenRefreshRefused() {
        Session session =
                aSession().withState(new SessionState.Revoked(EXCHANGED)).build();

        assertRefusedUnchanged(session, FIRST_HASH, NOW);
    }

    @Test
    void GivenAnAccountWhoseSessionsWereRevokedSinceSignIn_WhenTheCurrentTokenIsExchanged_ThenRefreshRefused() {
        Session session = aSession().build();
        SessionSnapshot before = session.snapshot();

        RefreshOutcome outcome =
                session.exchange(FIRST_HASH, ACCOUNT_GENERATION.next(), SECOND, SECOND_HASH, EXCHANGED);

        assertThat(outcome).isEqualTo(RefreshOutcome.refused());
        assertThat(session.snapshot()).isEqualTo(before);
    }

    @Test
    void GivenATokenExchanged30SecondsAgo_WhenPresentedAgain_ThenANewTokenReplacesTheSuccessorAndNothingIsRevoked() {
        Session session = exchangedOnce().build();
        Instant retry = EXCHANGED.plusSeconds(30);

        RefreshOutcome outcome = session.exchange(FIRST_HASH, ACCOUNT_GENERATION, THIRD, THIRD_HASH, retry);

        assertThat(outcome).isEqualTo(new RefreshOutcome.Issued(THIRD));
        assertThat(session.snapshot().tokens())
                .containsExactly(
                        token("1", "first", OPENED, new TokenState.Exchanged(EXCHANGED, THIRD)),
                        token("2", "second", EXCHANGED, new TokenState.Replaced(retry)),
                        token("3", "third", retry, new TokenState.Current()));
        assertThat(session.state()).isEqualTo(new SessionState.Live());
    }

    @Test
    void GivenATokenPresentedTwiceInItsGrace_WhenPresentedAThirdTime_ThenTheGraceStillCountsFromTheFirstExchange() {
        Session session = exchangedOnce().build();
        session.exchange(FIRST_HASH, ACCOUNT_GENERATION, THIRD, THIRD_HASH, EXCHANGED.plusSeconds(10));
        Session reloaded = Session.restore(session.snapshot());

        assertRefusedAndRevoked(reloaded, FIRST_HASH, EXCHANGED.plusSeconds(31));
    }

    @Test
    void GivenATokenExchanged31SecondsAgo_WhenPresentedAgain_ThenTheSessionIsRevokedAndRefreshRefused() {
        Session session = exchangedOnce().build();

        assertRefusedAndRevoked(session, FIRST_HASH, EXCHANGED.plusSeconds(31));
    }

    @Test
    void GivenAReplacedToken_WhenPresented_ThenTheSessionIsRevokedAndRefreshRefused() {
        Instant retry = EXCHANGED.plusSeconds(5);
        Session session = aSession()
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Exchanged(EXCHANGED, THIRD)),
                        token("2", "second", EXCHANGED, new TokenState.Replaced(retry)),
                        token("3", "third", retry, new TokenState.Current()))
                .build();

        assertRefusedAndRevoked(session, SECOND_HASH, retry.plusSeconds(1));
    }

    @Test
    void GivenATokenWhoseSuccessorWasAlsoExchanged_WhenPresentedInItsGrace_ThenTheSessionIsRevokedAndRefreshRefused() {
        Instant second = EXCHANGED.plusSeconds(5);
        Session session = aSession()
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Exchanged(EXCHANGED, SECOND)),
                        token("2", "second", EXCHANGED, new TokenState.Exchanged(second, THIRD)),
                        token("3", "third", second, new TokenState.Current()))
                .build();

        assertRefusedAndRevoked(session, FIRST_HASH, EXCHANGED.plusSeconds(10));
    }

    @Test
    void GivenATokenSupersededByAStepUp_WhenPresented_ThenRefreshRefusedAndTheSessionStaysLive() {
        Session session = aSession()
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Superseded(EXCHANGED)),
                        token("2", "second", EXCHANGED, new TokenState.Current()))
                .build();

        assertRefusedUnchanged(session, FIRST_HASH, EXCHANGED.plusSeconds(1));
    }

    @Test
    void GivenATokenWhoseSuccessorAStepUpSuperseded_WhenPresentedInItsGrace_ThenRefreshRefusedAndTheSessionStaysLive() {
        Instant stepUp = EXCHANGED.plusSeconds(5);
        Session session = aSession()
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Exchanged(EXCHANGED, SECOND)),
                        token("2", "second", EXCHANGED, new TokenState.Superseded(stepUp)),
                        token("3", "third", stepUp, new TokenState.Current()))
                .build();

        assertRefusedUnchanged(session, FIRST_HASH, EXCHANGED.plusSeconds(30));
    }

    @Test
    void
            GivenATokenWhoseSuccessorAStepUpSuperseded_WhenPresentedAfterItsGrace_ThenTheSessionIsRevokedAndRefreshRefused() {
        Instant stepUp = EXCHANGED.plusSeconds(5);
        Session session = aSession()
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Exchanged(EXCHANGED, SECOND)),
                        token("2", "second", EXCHANGED, new TokenState.Superseded(stepUp)),
                        token("3", "third", stepUp, new TokenState.Current()))
                .build();

        assertRefusedAndRevoked(session, FIRST_HASH, EXCHANGED.plusSeconds(31));
    }

    @Test
    void GivenATokenPresentedTwiceInItsGrace_WhenPresentedAThirdTimeInIt_ThenItsLatestSuccessorIsReplacedToo() {
        Session session = exchangedOnce().build();
        Instant second = EXCHANGED.plusSeconds(10);
        Instant third = EXCHANGED.plusSeconds(20);
        session.exchange(FIRST_HASH, ACCOUNT_GENERATION, THIRD, THIRD_HASH, second);

        RefreshOutcome outcome = session.exchange(FIRST_HASH, ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, third);

        assertThat(outcome).isEqualTo(new RefreshOutcome.Issued(FOURTH));
        assertThat(session.snapshot().tokens())
                .containsExactly(
                        token("1", "first", OPENED, new TokenState.Exchanged(EXCHANGED, FOURTH)),
                        token("2", "second", EXCHANGED, new TokenState.Replaced(second)),
                        token("3", "third", second, new TokenState.Replaced(third)),
                        token("4", "fourth", third, new TokenState.Current()));
        assertThat(session.state()).isEqualTo(new SessionState.Live());
    }

    // Pruning the chain

    @Test
    void GivenTokensUsedLongerAgoThanTheyAreKept_WhenTheCurrentOneIsExchanged_ThenTheyAreDropped() {
        Instant monthLater = OPENED.plus(Duration.ofDays(30));
        Session session = aSession()
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Exchanged(OPENED.plusSeconds(60), SECOND)),
                        token("2", "second", OPENED.plusSeconds(60), new TokenState.Exchanged(monthLater, THIRD)),
                        token("3", "third", monthLater, new TokenState.Current()))
                .build();
        Instant now = monthLater.plus(Duration.ofDays(1));

        session.exchange(THIRD_HASH, ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, now);

        assertThat(session.snapshot().tokens())
                .containsExactly(
                        token("3", "third", monthLater, new TokenState.Exchanged(now, FOURTH)),
                        token("4", "fourth", now, new TokenState.Current()));
    }

    @Test
    void GivenAnExchangedTokenIssuedExactlyTheKeptPeriodAgo_WhenTheChainIsPruned_ThenItIsKept() {
        Instant now = OPENED.plus(Session.TOKEN_KEPT_FOR);
        Session session = exchangedOnce().build();

        session.exchange(SECOND_HASH, ACCOUNT_GENERATION, THIRD, THIRD_HASH, EXCHANGED.plus(Duration.ofDays(13)));
        session.exchange(new TokenHash("not-ours"), ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, now);

        assertThat(session.snapshot().tokens())
                .extracting(RefreshTokenSnapshot::id)
                .contains(FIRST);
    }

    @Test
    void GivenATokenTheChainNoLongerKeeps_WhenReplayed_ThenRefreshRefusedWithoutRevokingTheSession() {
        Instant monthLater = OPENED.plus(Duration.ofDays(30));
        Session session = aSession()
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Exchanged(OPENED.plusSeconds(60), SECOND)),
                        token("2", "second", OPENED.plusSeconds(60), new TokenState.Current()))
                .build();

        RefreshOutcome outcome = session.exchange(FIRST_HASH, ACCOUNT_GENERATION, THIRD, THIRD_HASH, monthLater);

        assertThat(outcome).isEqualTo(RefreshOutcome.refused());
        assertThat(session.state()).isEqualTo(new SessionState.Live());
        assertThat(session.snapshot().tokens())
                .extracting(RefreshTokenSnapshot::id)
                .containsExactly(SECOND);
    }

    @Test
    void GivenTokensUsedLongerAgoThanTheyAreKept_WhenThePasswordIsConfirmed_ThenTheyAreDropped() {
        Instant monthLater = OPENED.plus(Duration.ofDays(30));
        Session session = aSession()
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Exchanged(monthLater, SECOND)),
                        token("2", "second", monthLater, new TokenState.Current()))
                .build();
        Instant now = monthLater.plusSeconds(60);

        session.confirmPassword(ACCOUNT_GENERATION, THIRD, THIRD_HASH, now);

        assertThat(session.snapshot().tokens())
                .containsExactly(
                        token("2", "second", monthLater, new TokenState.Superseded(now)),
                        token("3", "third", now, new TokenState.Current()));
    }

    // Giving the password again

    @Test
    void GivenALiveSession_WhenThePasswordIsConfirmed_ThenItGetsNewTokensUnderANewGeneration() {
        Session session = aSession().build();

        session.confirmPassword(ACCOUNT_GENERATION, SECOND, SECOND_HASH, NOW);

        SessionSnapshot after = session.snapshot();
        assertThat(after.generation()).isEqualTo(SESSION_GENERATION.next());
        assertThat(after.lastPasswordAt()).isEqualTo(NOW);
        assertThat(after.tokens())
                .containsExactly(
                        token("1", "first", OPENED, new TokenState.Superseded(NOW)),
                        token("2", "second", NOW, new TokenState.Current()));
        assertThat(session.pendingEvents()).isEmpty();
    }

    @Test
    void GivenARevokedSession_WhenThePasswordIsConfirmed_ThenSessionRevoked() {
        Session session = aSession().withState(new SessionState.Revoked(OPENED)).build();

        assertRefusedUnchanged(
                session,
                () -> session.confirmPassword(ACCOUNT_GENERATION, SECOND, SECOND_HASH, NOW),
                new SessionRevoked());
    }

    @Test
    void GivenAnAccountWhoseSessionsWereRevokedSinceSignIn_WhenThePasswordIsConfirmed_ThenSessionRevoked() {
        Session session = aSession().build();

        assertRefusedUnchanged(
                session,
                () -> session.confirmPassword(ACCOUNT_GENERATION.next(), SECOND, SECOND_HASH, NOW),
                new SessionRevoked());
    }

    @Test
    void GivenAnAccountWhoseSessionsWereRevokedSinceSignIn_WhenAnAdminOnlyOperationAsks_ThenSessionRevoked() {
        Session session = aSession().build();

        assertRefusedUnchanged(
                session, () -> session.requireFreshStepUp(ACCOUNT_GENERATION.next(), OPENED), new SessionRevoked());
    }

    @Test
    void GivenThePasswordGivenFiveMinutesAgo_WhenAnAdminOnlyOperationAsks_ThenTheStepUpIsFresh() {
        Session session = aSession().withLastPasswordAt(NOW).build();

        session.requireFreshStepUp(ACCOUNT_GENERATION, NOW.plus(Duration.ofMinutes(5)));

        assertThat(session.pendingEvents()).isEmpty();
    }

    @Test
    void GivenThePasswordGivenFiveMinutesAndASecondAgo_WhenAnAdminOnlyOperationAsks_ThenStepUpRequired() {
        Session session = aSession().withLastPasswordAt(NOW).build();

        assertRefusedUnchanged(
                session,
                () -> session.requireFreshStepUp(
                        ACCOUNT_GENERATION, NOW.plus(Duration.ofMinutes(5)).plusSeconds(1)),
                new StepUpRequired());
    }

    @Test
    void GivenASessionJustOpened_WhenAnAdminOnlyOperationAsks_ThenTheSignInCountsAsGivingThePassword() {
        Session session = Session.open(SESSION, AccountBuilder.anAccount().build(), FIRST, FIRST_HASH, OPENED);

        session.requireFreshStepUp(ACCOUNT_GENERATION, OPENED.plus(Duration.ofMinutes(4)));

        assertThat(session.pendingEvents()).isEmpty();
    }

    @Test
    void GivenARevokedSession_WhenAnAdminOnlyOperationAsks_ThenSessionRevoked() {
        Session session = aSession().withState(new SessionState.Revoked(OPENED)).build();

        assertRefusedUnchanged(
                session, () -> session.requireFreshStepUp(ACCOUNT_GENERATION, OPENED), new SessionRevoked());
    }

    // Contact times

    @Test
    void GivenNoContactYet_WhenContactIsRecorded_ThenItsTimeIsKeptAndTheVersionStays() {
        Session session = aSession().build();

        session.recordContact(NOW);

        assertThat(session.snapshot().contactTimes()).containsExactly(NOW);
        assertThat(session.version()).isEqualTo(VERSION);
        assertThat(session.generation()).isEqualTo(SESSION_GENERATION);
        assertThat(session.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAContactUnderAMinuteAgo_WhenContactIsRecorded_ThenNoNewTimeIsKept() {
        Session session = aSession().withContactTimes(NOW).build();

        session.recordContact(NOW.plusSeconds(59));

        assertThat(session.snapshot().contactTimes()).containsExactly(NOW);
    }

    @Test
    void GivenAContactAMinuteAgo_WhenContactIsRecorded_ThenTheNewTimeIsKeptToo() {
        Session session = aSession().withContactTimes(NOW).build();

        session.recordContact(NOW.plusSeconds(60));

        assertThat(session.snapshot().contactTimes()).containsExactly(NOW, NOW.plusSeconds(60));
    }

    @Test
    void GivenContactsOlderThan14Days_WhenContactIsRecorded_ThenTheyAreDroppedAndTheRestKept() {
        Instant later = NOW.plus(Duration.ofDays(15));
        Instant fifteenDaysBefore = later.minus(Duration.ofDays(15));
        Instant fourteenDaysBefore = later.minus(Duration.ofDays(14));
        Session session = aSession()
                .withContactTimes(fifteenDaysBefore, fourteenDaysBefore, later.minusSeconds(30))
                .build();

        session.recordContact(later);

        assertThat(session.snapshot().contactTimes()).containsExactly(fourteenDaysBefore, later.minusSeconds(30));
    }

    @Test
    void GivenAContact14DaysAndOneSecondOld_WhenContactIsRecorded_ThenItIsDropped() {
        Session session = aSession().withContactTimes(NOW).build();
        Instant later = NOW.plus(Duration.ofDays(14)).plusSeconds(1);

        session.recordContact(later);

        assertThat(session.snapshot().contactTimes()).containsExactly(later);
    }

    @Test
    void GivenOldContactTimesOfARevokedSession_WhenTheRetentionJobRuns_ThenTheyAreDroppedWithoutANewContact() {
        Instant later = NOW.plus(Duration.ofDays(14)).plusSeconds(1);
        Session session = aSession()
                .withState(new SessionState.Revoked(NOW))
                .withContactTimes(NOW, later.minusSeconds(60))
                .build();

        session.dropOldContacts(later);

        assertThat(session.snapshot().contactTimes()).containsExactly(later.minusSeconds(60));
        assertThat(session.version()).isEqualTo(VERSION);
    }

    @Test
    void GivenARevokedSession_WhenContactIsRecorded_ThenSessionRevoked() {
        Session session = aSession().withState(new SessionState.Revoked(OPENED)).build();

        assertRefusedUnchanged(session, () -> session.recordContact(NOW), new SessionRevoked());
    }

    // Signing out

    @Test
    void GivenALiveSession_WhenSignedOut_ThenItIsRevoked() {
        Session session = aSession().build();

        session.revoke(NOW);

        assertThat(session.state()).isEqualTo(new SessionState.Revoked(NOW));
        assertThat(session.pendingEvents()).isEmpty();
    }

    // When it ended

    @Test
    void GivenALiveSession_WhenAskedWhenItEnds_ThenItIsItsCurrentTokensExpiry() {
        Session session = exchangedOnce().build();

        assertThat(session.endedAt()).isEqualTo(EXCHANGED.plus(Duration.ofDays(14)));
    }

    @Test
    void GivenASessionRevokedBeforeItsTokenExpired_WhenAskedWhenItEnded_ThenItIsTheRevocation() {
        Session session = aSession().withState(new SessionState.Revoked(NOW)).build();

        assertThat(session.endedAt()).isEqualTo(NOW);
    }

    @Test
    void GivenARevokedSessionWithNoCurrentToken_WhenAskedWhenItEnded_ThenItIsTheRevocation() {
        Instant retry = EXCHANGED.plusSeconds(5);
        Session session = aSession()
                .withState(new SessionState.Revoked(NOW))
                .withTokens(
                        token("1", "first", OPENED, new TokenState.Exchanged(EXCHANGED, SECOND)),
                        token("2", "second", EXCHANGED, new TokenState.Replaced(retry)))
                .build();

        assertThat(session.endedAt()).isEqualTo(NOW);
    }

    @Test
    void GivenARevokedSession_WhenSignedOutAgain_ThenItKeepsItsFirstRevocation() {
        Session session = aSession().withState(new SessionState.Revoked(OPENED)).build();

        session.revoke(NOW);

        assertThat(session.state()).isEqualTo(new SessionState.Revoked(OPENED));
    }
}
