package com.dispatchflow.access.domain.session;

import static com.dispatchflow.access.domain.session.SessionBuilder.ACCOUNT_GENERATION;
import static com.dispatchflow.access.domain.session.SessionBuilder.FIRST;
import static com.dispatchflow.access.domain.session.SessionBuilder.FIRST_HASH;
import static com.dispatchflow.access.domain.session.SessionBuilder.LOADED;
import static com.dispatchflow.access.domain.session.SessionBuilder.OPENED;
import static com.dispatchflow.access.domain.session.SessionBuilder.SESSION;
import static com.dispatchflow.access.domain.session.SessionBuilder.aSession;
import static com.dispatchflow.access.domain.session.SessionBuilder.token;
import static com.dispatchflow.shared.AsSaved.asSaved;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.access.domain.AccessError.AccountDeactivated;
import com.dispatchflow.access.domain.AccessError.AccountHasNoRole;
import com.dispatchflow.access.domain.AccessError.PasswordNotSet;
import com.dispatchflow.access.domain.AccessError.SessionRevoked;
import com.dispatchflow.access.domain.AccessError.StepUpRequired;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.account.AccountBuilder;
import com.dispatchflow.access.domain.account.AccountState;
import com.dispatchflow.access.domain.account.UserAccount;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.PersonalData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static final RefreshTokenId SECOND = SessionBuilder.tokenId("2");
    private static final TokenHash SECOND_HASH = new TokenHash("second");
    private static final RefreshTokenId THIRD = SessionBuilder.tokenId("3");
    private static final TokenHash THIRD_HASH = new TokenHash("third");
    private static final RefreshTokenId FOURTH = SessionBuilder.tokenId("4");
    private static final TokenHash FOURTH_HASH = new TokenHash("fourth");
    private static final Instant EXCHANGED = OPENED.plus(Duration.ofMinutes(15));
    private static final Instant NOW = OPENED.plus(Duration.ofHours(2));
    /** Under a refresh token's 14 days, so a token exchanged this long after its issue still works. */
    private static final Duration THIRTEEN_DAYS = Duration.ofDays(13);

    /** Asserts the refusal through the shared helper, and that it changed nothing. */
    private static void assertRefusedUnchanged(Session session, ThrowingCallable call, DomainError expected) {
        SessionDescription before = SessionDescription.of(session);

        assertRefused(call, expected);

        assertThat(SessionDescription.of(session)).isEqualTo(before);
        assertThat(session.pendingEvents()).isEmpty();
    }

    private static void assertRefusedUnchanged(Session session, TokenHash presented, Instant at) {
        SessionDescription before = SessionDescription.of(session);

        RefreshOutcome outcome = session.exchange(presented, ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, at);

        assertThat(outcome).isEqualTo(RefreshOutcome.refused());
        assertThat(SessionDescription.of(session)).isEqualTo(before);
    }

    private static void assertRefusedAndRevoked(Session session, TokenHash presented, Instant at) {
        RefreshOutcome outcome = session.exchange(presented, ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, at);

        assertThat(outcome).isEqualTo(RefreshOutcome.refused());
        assertThat(session.state()).isEqualTo(new SessionState.Revoked(at));
        assertThat(session.tokens()).noneMatch(stored -> stored.id().equals(FOURTH));
    }

    /** A session whose first token was exchanged at {@link #EXCHANGED} for the second, still current. */
    private static SessionBuilder exchangedOnce() {
        return aSession().exchanged("first", "2", "second", EXCHANGED);
    }

    /** Asserts the call fails as a programming error with the message, and changed nothing. */
    private static void assertBugUnchanged(Session session, ThrowingCallable call, String message) {
        SessionDescription before = SessionDescription.of(session);

        assertThatThrownBy(call).isInstanceOf(AssertionError.class).hasMessage(message);

        assertThat(SessionDescription.of(session)).isEqualTo(before);
        assertThat(session.pendingEvents()).isEmpty();
    }

    @Test
    void GivenASessionWithAChainOfTokens_WhenItIsPrinted_ThenNoTokenHashShows() {
        Session session = exchangedOnce().build();

        assertThat(session.toString() + session.tokens()).doesNotContain(FIRST_HASH.value(), SECOND_HASH.value());
    }

    @Test
    void GivenASessionWithContactTimes_WhenItIsPrinted_ThenNoContactTimeShows() {
        Instant contact = Instant.parse("2027-06-01T07:13:00Z");
        Session session = aSession().withContactTimes(contact).build();

        assertThat(session).isInstanceOf(PersonalData.class);
        assertThat(session.toString()).contains("redacted").doesNotContain("07:13");
    }

    @Test
    void GivenASessionWithAChainOfTokens_WhenItIsPrinted_ThenItNamesHowManyAndNoTokenTime() {
        Session session = exchangedOnce().build();

        assertThat(session.toString() + session.tokens())
                .contains("tokens=2 redacted")
                .doesNotContain(EXCHANGED.toString());
    }

    @Test
    void GivenARevokedSession_WhenItIsPrinted_ThenNoneOfItsTimesShows() {
        Instant passwordGiven = OPENED.plus(Duration.ofMinutes(40));
        Instant revoked = OPENED.plus(Duration.ofHours(3));
        Session session = aSession()
                .steppedUpAt(passwordGiven)
                .withState(new SessionState.Revoked(revoked))
                .build();

        assertThat(session.toString())
                .contains("openedAt=redacted", "lastPasswordAt=redacted", "state=Revoked[at=redacted]")
                .doesNotContain(OPENED.toString(), passwordGiven.toString(), revoked.toString());
    }

    @Test
    void GivenALiveSession_WhenItIsPrinted_ThenItNamesItsState() {
        Session session = aSession().build();

        assertThat(session.toString()).contains("state=Live[]").doesNotContain(OPENED.toString());
    }

    @Test
    void GivenATokenIdTheChainHolds_WhenAnExchangeIssuesItAgain_ThenTheBugIsReported() {
        Session session = aSession().build();

        assertBugUnchanged(
                session,
                () -> session.exchange(FIRST_HASH, ACCOUNT_GENERATION, FIRST, SECOND_HASH, EXCHANGED),
                "a new token needs a new ID");
    }

    @Test
    void GivenATokenHashTheChainHolds_WhenAnExchangeIssuesItAgain_ThenTheBugIsReported() {
        Session session = aSession().build();

        assertBugUnchanged(
                session,
                () -> session.exchange(FIRST_HASH, ACCOUNT_GENERATION, SECOND, FIRST_HASH, EXCHANGED),
                "a new token needs a new hash");
    }

    @Test
    void GivenATokenIdOrHashTheChainHolds_WhenThePasswordIsConfirmedWithIt_ThenTheBugIsReported() {
        Session session = aSession().build();

        assertBugUnchanged(
                session,
                () -> session.confirmPassword(ACCOUNT_GENERATION, FIRST, SECOND_HASH, NOW),
                "a new token needs a new ID");
        assertBugUnchanged(
                session,
                () -> session.confirmPassword(ACCOUNT_GENERATION, SECOND, FIRST_HASH, NOW),
                "a new token needs a new hash");
    }

    // Opening

    @Test
    void GivenAnAccountThatMaySignIn_WhenASessionOpens_ThenItIsLiveUnderTheAccountsGenerationWithOneCurrentToken() {
        UserAccount account = AccountBuilder.anAccount().build();

        Session session = Session.open(SESSION, account, FIRST, FIRST_HASH, OPENED);

        assertThat(session.savedVersion()).isEmpty();
        assertThat(SessionDescription.of(session))
                .isEqualTo(new SessionDescription(
                        SESSION,
                        AccountBuilder.ACCOUNT,
                        account.generation(),
                        SessionGeneration.INITIAL,
                        new SessionState.Live(),
                        OPENED,
                        OPENED,
                        List.of(),
                        List.of(token("1", "first", OPENED, new TokenState.Current()))));
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
    void GivenAnAccountWithNoPasswordYet_WhenASessionOpens_ThenPasswordNotSet() {
        UserAccount account = AccountBuilder.anAccount().withoutPassword().build();

        assertRefused(() -> Session.open(SESSION, account, FIRST, FIRST_HASH, OPENED), new PasswordNotSet());
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
        assertThat(TokenDescription.everyTokenOf(session))
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

    /** Each token was exchanged 13 days after its issue, so the session is 26 days old already. */
    @Test
    void GivenALongLivedSession_WhenItsLatestTokenIsUnder14DaysOld_ThenItIsExchangedWithNoCapOnTheSession() {
        Instant secondIssued = OPENED.plus(THIRTEEN_DAYS);
        Instant thirdIssued = secondIssued.plus(THIRTEEN_DAYS);
        Session session = aSession()
                .exchanged("first", "2", "second", secondIssued)
                .exchanged("second", "3", "third", thirdIssued)
                .build();

        assertThat(session.exchange(
                        THIRD_HASH, ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, thirdIssued.plus(THIRTEEN_DAYS)))
                .isEqualTo(new RefreshOutcome.Issued(FOURTH));
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
        SessionDescription before = SessionDescription.of(session);

        RefreshOutcome outcome =
                session.exchange(FIRST_HASH, ACCOUNT_GENERATION.next(), SECOND, SECOND_HASH, EXCHANGED);

        assertThat(outcome).isEqualTo(RefreshOutcome.refused());
        assertThat(SessionDescription.of(session)).isEqualTo(before);
    }

    @Test
    void GivenATokenExchanged30SecondsAgo_WhenPresentedAgain_ThenANewTokenReplacesTheSuccessorAndNothingIsRevoked() {
        Session session = exchangedOnce().build();
        Instant retry = EXCHANGED.plusSeconds(30);

        RefreshOutcome outcome = session.exchange(FIRST_HASH, ACCOUNT_GENERATION, THIRD, THIRD_HASH, retry);

        assertThat(outcome).isEqualTo(new RefreshOutcome.Issued(THIRD));
        assertThat(TokenDescription.everyTokenOf(session))
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
        Session saved = asSaved(session);

        assertRefusedAndRevoked(saved, FIRST_HASH, EXCHANGED.plusSeconds(31));
    }

    @Test
    void GivenATokenExchanged31SecondsAgo_WhenPresentedAgain_ThenTheSessionIsRevokedAndRefreshRefused() {
        Session session = exchangedOnce().build();

        assertRefusedAndRevoked(session, FIRST_HASH, EXCHANGED.plusSeconds(31));
    }

    @Test
    void GivenAReplacedToken_WhenPresented_ThenTheSessionIsRevokedAndRefreshRefused() {
        Instant retry = EXCHANGED.plusSeconds(5);
        Session session =
                exchangedOnce().exchanged("first", "3", "third", retry).build();

        assertRefusedAndRevoked(session, SECOND_HASH, retry.plusSeconds(1));
    }

    @Test
    void GivenATokenWhoseSuccessorWasAlsoExchanged_WhenPresentedInItsGrace_ThenTheSessionIsRevokedAndRefreshRefused() {
        Instant second = EXCHANGED.plusSeconds(5);
        Session session =
                exchangedOnce().exchanged("second", "3", "third", second).build();

        assertRefusedAndRevoked(session, FIRST_HASH, EXCHANGED.plusSeconds(10));
    }

    @Test
    void GivenATokenSupersededByAStepUp_WhenPresented_ThenRefreshRefusedAndTheSessionStaysLive() {
        Session session = aSession().steppedUp("2", "second", EXCHANGED).build();

        assertRefusedUnchanged(session, FIRST_HASH, EXCHANGED.plusSeconds(1));
    }

    @Test
    void GivenATokenWhoseSuccessorAStepUpSuperseded_WhenPresentedInItsGrace_ThenRefreshRefusedAndTheSessionStaysLive() {
        Instant stepUp = EXCHANGED.plusSeconds(5);
        Session session = exchangedOnce().steppedUp("3", "third", stepUp).build();

        assertRefusedUnchanged(session, FIRST_HASH, EXCHANGED.plusSeconds(30));
    }

    @Test
    void
            GivenATokenWhoseSuccessorAStepUpSuperseded_WhenPresentedAfterItsGrace_ThenTheSessionIsRevokedAndRefreshRefused() {
        Instant stepUp = EXCHANGED.plusSeconds(5);
        Session session = exchangedOnce().steppedUp("3", "third", stepUp).build();

        assertRefusedAndRevoked(session, FIRST_HASH, EXCHANGED.plusSeconds(31));
    }

    @Test
    void GivenATokenPresentedTwiceInItsGrace_WhenPresentedAThirdTimeInIt_ThenItsLatestSuccessorIsReplacedToo() {
        Session session = exchangedOnce().build();
        Instant second = EXCHANGED.plusSeconds(10);
        Instant third = EXCHANGED.plusSeconds(20);
        session.exchange(FIRST_HASH, ACCOUNT_GENERATION, THIRD, THIRD_HASH, second);
        Session saved = asSaved(session);

        RefreshOutcome outcome = saved.exchange(FIRST_HASH, ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, third);

        assertThat(outcome).isEqualTo(new RefreshOutcome.Issued(FOURTH));
        assertThat(TokenDescription.everyTokenOf(saved))
                .containsExactly(
                        token("1", "first", OPENED, new TokenState.Exchanged(EXCHANGED, FOURTH)),
                        token("2", "second", EXCHANGED, new TokenState.Replaced(second)),
                        token("3", "third", second, new TokenState.Replaced(third)),
                        token("4", "fourth", third, new TokenState.Current()));
        assertThat(saved.state()).isEqualTo(new SessionState.Live());
    }

    // Pruning the chain

    /** Every exchange came within its token's 14 days, the last a day before the one tested. */
    @Test
    void GivenTokensUsedLongerAgoThanTheyAreKept_WhenTheCurrentOneIsExchanged_ThenTheyAreDropped() {
        Instant secondIssued = OPENED.plusSeconds(60);
        Instant thirdIssued = secondIssued.plus(THIRTEEN_DAYS);
        Session session = aSession()
                .exchanged("first", "2", "second", secondIssued)
                .exchanged("second", "3", "third", thirdIssued)
                .build();
        Instant now = secondIssued.plus(Duration.ofDays(15));

        session.exchange(THIRD_HASH, ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, now);

        assertThat(TokenDescription.everyTokenOf(session))
                .containsExactly(
                        token("3", "third", thirdIssued, new TokenState.Exchanged(now, FOURTH)),
                        token("4", "fourth", now, new TokenState.Current()));
    }

    @Test
    void GivenAnExchangedTokenIssuedExactlyTheKeptPeriodAgo_WhenTheChainIsPruned_ThenItIsKept() {
        Instant now = OPENED.plus(Session.TOKEN_KEPT_FOR);
        Session session = exchangedOnce().build();

        session.exchange(SECOND_HASH, ACCOUNT_GENERATION, THIRD, THIRD_HASH, EXCHANGED.plus(THIRTEEN_DAYS));
        session.exchange(new TokenHash("not-ours"), ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, now);

        assertThat(session.tokens()).extracting(RefreshToken::id).contains(FIRST);
    }

    @Test
    void GivenAClockThatSteppedBackBetweenTwoTokens_WhenTheCutOffFallsBetweenThem_ThenTheNamedSuccessorIsKept() {
        // A was issued at T, then the clock stepped back: its exchange issued B at T - 8 s.
        Instant t = OPENED.plus(Duration.ofDays(1));
        Instant steppedBack = t.minusSeconds(8);
        Instant dayLater = t.plus(Duration.ofDays(1));
        Session session = aSession()
                .openedAt(t)
                .exchanged("first", "2", "second", steppedBack)
                .exchanged("second", "3", "third", dayLater)
                .build();
        Instant now = steppedBack.plus(Session.TOKEN_KEPT_FOR).plusSeconds(4);

        RefreshOutcome outcome = session.exchange(THIRD_HASH, ACCOUNT_GENERATION, FOURTH, FOURTH_HASH, now);

        assertThat(outcome).isEqualTo(new RefreshOutcome.Issued(FOURTH));
        assertThat(session.tokens()).extracting(RefreshToken::id).containsExactly(FIRST, SECOND, THIRD, FOURTH);
    }

    @Test
    void GivenATokenTheChainNoLongerKeeps_WhenReplayed_ThenRefreshRefusedWithoutRevokingTheSession() {
        Instant monthLater = OPENED.plus(Duration.ofDays(30));
        Session session = aSession()
                .exchanged("first", "2", "second", OPENED.plusSeconds(60))
                .build();

        RefreshOutcome outcome = session.exchange(FIRST_HASH, ACCOUNT_GENERATION, THIRD, THIRD_HASH, monthLater);

        assertThat(outcome).isEqualTo(RefreshOutcome.refused());
        assertThat(session.state()).isEqualTo(new SessionState.Live());
        assertThat(session.tokens()).extracting(RefreshToken::id).containsExactly(SECOND);
    }

    /** The first token was exchanged within its 14 days, and the password confirmed two days later. */
    @Test
    void GivenTokensUsedLongerAgoThanTheyAreKept_WhenThePasswordIsConfirmed_ThenTheyAreDropped() {
        Instant secondIssued = OPENED.plus(THIRTEEN_DAYS);
        Session session =
                aSession().exchanged("first", "2", "second", secondIssued).build();
        Instant now = secondIssued.plus(Duration.ofDays(2));

        session.confirmPassword(ACCOUNT_GENERATION, THIRD, THIRD_HASH, now);

        assertThat(TokenDescription.everyTokenOf(session))
                .containsExactly(
                        token("2", "second", secondIssued, new TokenState.Superseded(now)),
                        token("3", "third", now, new TokenState.Current()));
    }

    // Giving the password again

    @Test
    void GivenALiveSession_WhenThePasswordIsConfirmed_ThenItGetsNewTokensUnderANewGeneration() {
        Session session = aSession().build();
        SessionGeneration before = session.generation();

        session.confirmPassword(ACCOUNT_GENERATION, SECOND, SECOND_HASH, NOW);

        assertThat(session.generation()).isEqualTo(before.next());
        assertThat(session.lastPasswordAt()).isEqualTo(NOW);
        assertThat(TokenDescription.everyTokenOf(session))
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
        Session session = aSession().steppedUpAt(NOW).build();

        session.requireFreshStepUp(ACCOUNT_GENERATION, NOW.plus(Duration.ofMinutes(5)));

        assertThat(session.pendingEvents()).isEmpty();
    }

    @Test
    void GivenThePasswordGivenFiveMinutesAndASecondAgo_WhenAnAdminOnlyOperationAsks_ThenStepUpRequired() {
        Session session = aSession().steppedUpAt(NOW).build();

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
        SessionGeneration before = session.generation();

        session.recordContact(NOW);

        assertThat(session.contactTimes()).containsExactly(NOW);
        assertThat(session.version()).isEqualTo(LOADED);
        assertThat(session.generation()).isEqualTo(before);
        assertThat(session.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAContactUnderAMinuteAgo_WhenContactIsRecorded_ThenNoNewTimeIsKept() {
        Session session = aSession().withContactTimes(NOW).build();

        session.recordContact(NOW.plusSeconds(59));

        assertThat(session.contactTimes()).containsExactly(NOW);
    }

    @Test
    void GivenAContactAMinuteAgo_WhenContactIsRecorded_ThenTheNewTimeIsKeptToo() {
        Session session = aSession().withContactTimes(NOW).build();

        session.recordContact(NOW.plusSeconds(60));

        assertThat(session.contactTimes()).containsExactly(NOW, NOW.plusSeconds(60));
    }

    /**
     * A contact recorded a day after the first keeps it, so the first is 15 days old and the second
     * 14 when the next contact comes.
     */
    @Test
    void GivenContactsOlderThan14Days_WhenContactIsRecorded_ThenTheyAreDroppedAndTheRestKept() {
        Instant later = NOW.plus(Duration.ofDays(15));
        Instant fifteenDaysBefore = later.minus(Duration.ofDays(15));
        Instant fourteenDaysBefore = later.minus(Duration.ofDays(14));
        Session session = aSession()
                .withContactTimes(fifteenDaysBefore, fourteenDaysBefore)
                .build();

        session.recordContact(later);

        assertThat(session.contactTimes()).containsExactly(fourteenDaysBefore, later);
    }

    @Test
    void GivenAContact14DaysAndOneSecondOld_WhenContactIsRecorded_ThenItIsDropped() {
        Session session = aSession().withContactTimes(NOW).build();
        Instant later = NOW.plus(Duration.ofDays(14)).plusSeconds(1);

        session.recordContact(later);

        assertThat(session.contactTimes()).containsExactly(later);
    }

    /** Its contacts came before it was signed out, the last a minute before the job runs. */
    @Test
    void GivenOldContactTimesOfARevokedSession_WhenTheRetentionJobRuns_ThenTheyAreDroppedWithoutANewContact() {
        Instant later = NOW.plus(Duration.ofDays(14)).plusSeconds(1);
        Session session = aSession()
                .withContactTimes(NOW, later.minusSeconds(60))
                .withState(new SessionState.Revoked(later.minusSeconds(30)))
                .build();

        session.dropOldContacts(later);

        assertThat(session.contactTimes()).containsExactly(later.minusSeconds(60));
        assertThat(session.version()).isEqualTo(LOADED);
    }

    @Test
    void GivenAContactAtAnInstant_WhenContactIsRecordedAtThatInstantAgain_ThenNoTimeIsAdded() {
        Session session = aSession().withContactTimes(NOW).build();

        session.recordContact(NOW);

        assertThat(session.contactTimes()).containsExactly(NOW);
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

    @Test
    void GivenALiveSessionBegunBeforeItsAccountsRevocation_WhenTheRevocationIsHeard_ThenItIsRevokedAtThatTime() {
        Session session = aSession().build();

        session.revokeWithAccount(ACCOUNT_GENERATION.next(), NOW);

        assertThat(session.state()).isEqualTo(new SessionState.Revoked(NOW));
        assertThat(TokenDescription.everyTokenOf(session))
                .isEqualTo(TokenDescription.everyTokenOf(aSession().build()));
        assertThat(session.pendingEvents()).isEmpty();
    }

    @Test
    void GivenASessionBegunUnderTheGenerationARevocationLeft_WhenThatRevocationIsHeard_ThenItStaysLive() {
        Session session = aSession().build();

        session.revokeWithAccount(ACCOUNT_GENERATION, NOW);

        assertThat(SessionDescription.of(session))
                .isEqualTo(SessionDescription.of(aSession().build()));
    }

    /** Signed out after its account's revocation, before the listener ran: the revocation came first. */
    @Test
    void GivenASessionSignedOutAfterItsAccountsRevocation_WhenTheRevocationIsHeard_ThenItIsDatedByTheRevocation() {
        Instant accountRevokedAt = NOW.minus(Duration.ofMinutes(5));
        Session session = aSession().withState(new SessionState.Revoked(NOW)).build();

        session.revokeWithAccount(ACCOUNT_GENERATION.next(), accountRevokedAt);

        assertThat(session.state()).isEqualTo(new SessionState.Revoked(accountRevokedAt));
        assertThat(session.endedAt()).isEqualTo(accountRevokedAt);
    }

    @Test
    void GivenASessionBegunUnderTheGenerationARevocationLeft_WhenThatEarlierRevocationIsHeard_ThenItKeepsItsSignOut() {
        Session session = aSession().withState(new SessionState.Revoked(NOW)).build();

        session.revokeWithAccount(ACCOUNT_GENERATION, NOW.minus(Duration.ofMinutes(5)));

        assertThat(session.state()).isEqualTo(new SessionState.Revoked(NOW));
    }

    @Test
    void GivenARevokedSession_WhenItsAccountsRevocationIsHeard_ThenItKeepsItsFirstRevocation() {
        Session session = aSession().withState(new SessionState.Revoked(OPENED)).build();

        session.revokeWithAccount(ACCOUNT_GENERATION.next(), NOW);

        assertThat(session.state()).isEqualTo(new SessionState.Revoked(OPENED));
    }

    // When it ended

    @Test
    void GivenASessionItsAccountsRevocationEnded_WhenAskedWhenItEnded_ThenItIsTheRevocationNotItsTokensExpiry() {
        Session session = aSession().build();

        session.revokeWithAccount(ACCOUNT_GENERATION.next(), NOW);

        assertThat(session.endedAt()).isEqualTo(NOW);
    }

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
    void GivenARevokedSession_WhenSignedOutAgain_ThenItKeepsItsFirstRevocation() {
        Session session = aSession().withState(new SessionState.Revoked(OPENED)).build();

        session.revoke(NOW);

        assertThat(session.state()).isEqualTo(new SessionState.Revoked(OPENED));
    }
}
