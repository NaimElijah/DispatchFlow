package com.dispatchflow.access.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.EmailHolder;
import com.dispatchflow.access.domain.account.PasswordHash;
import com.dispatchflow.access.domain.account.Role;
import com.dispatchflow.access.domain.account.RoleSet;
import com.dispatchflow.access.domain.account.UserAccount;
import com.dispatchflow.access.domain.session.RefreshOutcome;
import com.dispatchflow.access.domain.session.RefreshTokenId;
import com.dispatchflow.access.domain.session.Session;
import com.dispatchflow.access.domain.session.SessionDescription;
import com.dispatchflow.access.domain.session.SessionGeneration;
import com.dispatchflow.access.domain.session.SessionState;
import com.dispatchflow.access.domain.session.TokenState;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link SessionRepository} against a real PostgreSQL (ADR-126, ADR-127): what a session stores and
 * loads as, its version and the contact times that leave it alone, the revocation written without a
 * version check, the lookups, retention, and the stored states the load check refuses.
 *
 * <p>The database is shared and never cleaned, and some lookups read every session. So test {@code
 * n} keys its own sessions ({@code session(n)}, {@code session(100 + n)}, …) and accounts, and the
 * retention tests work in 2021, years before every other test's sessions end. Token {@code m} of
 * session {@code n} is {@code token(n, m)}. Each getter is read outside the transaction that loaded
 * the session, which shows the session was loaded whole.
 */
@IntegrationTest
class SessionPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_7365_7301L;
    private static final Instant T = Instant.parse("2031-04-01T06:00:00Z");
    private static final AccountGeneration FIRST = AccountGeneration.of(1);

    @Autowired
    private SessionRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    // --- round trips (ADR-127 decision 1)

    /** The states a session stores and loads in, reached through commands. */
    enum Scenario {
        LIVE,
        REVOKED,
        CONTACT_TIMES,
        STEPPED_UP,
        EVERY_TOKEN_STATE,
        REVOKED_BY_REUSE
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenASession_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(Scenario scenario) {
        int n = 1 + scenario.ordinal();
        Session added = transactions.inTransaction(() -> {
            Session session = switch (scenario) {
                case LIVE -> opened(n);
                case REVOKED -> {
                    Session revoked = opened(n);
                    revoked.revoke(at(20));
                    yield revoked;
                }
                case CONTACT_TIMES -> {
                    Session contacted = opened(n);
                    contacted.recordContact(T);
                    contacted.recordContact(at(20));
                    contacted.recordContact(at(40));
                    yield contacted;
                }
                case STEPPED_UP -> {
                    Session stepped = opened(n);
                    stepped.confirmPassword(FIRST, token(n, 2), hash(n, 2), at(20));
                    yield stepped;
                }
                case EVERY_TOKEN_STATE -> everyTokenState(n);
                case REVOKED_BY_REUSE -> {
                    Session reused = exchanged(n);
                    reused.exchange(hash(n, 1), FIRST, token(n, 3), hash(n, 3), at(21));
                    yield reused;
                }
            };
            repository.add(session);
            return session;
        });

        Session loaded =
                transactions.inTransaction(() -> repository.get(session(n)).orElseThrow());

        assertThat(SessionDescription.of(loaded)).isEqualTo(SessionDescription.of(added));
        assertThat(loaded.id()).isEqualTo(session(n));
        assertThat(loaded.account()).isEqualTo(account(n));
        assertThat(loaded.accountGeneration()).isEqualTo(FIRST);
        assertThat(loaded.generation()).isEqualTo(added.generation());
        assertThat(loaded.state()).isEqualTo(added.state());
        assertThat(loaded.endedAt()).isEqualTo(added.endedAt());
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.changedBeyondContactTimes()).isFalse();
    }

    @Test
    void GivenNoSessionWithAnId_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(session(7)))).isEmpty();
    }

    // --- versions, conflicts and contact times (DL §4.1)

    @Test
    void GivenAnAddedSession_WhenItIsRefreshedAndThenSteppedUp_ThenTheVersionIsZeroThenOneThenTwo() {
        int n = 8;
        transactions.inTransaction(() -> repository.add(opened(n)));
        long added = storedVersion(n);

        transactions.inTransaction(() -> {
            Session session = repository.get(session(n)).orElseThrow();
            session.exchange(hash(n, 1), FIRST, token(n, 2), hash(n, 2), at(20));
            repository.save(session);
        });
        long refreshed = storedVersion(n);
        transactions.inTransaction(() -> {
            Session session = repository.get(session(n)).orElseThrow();
            session.confirmPassword(FIRST, token(n, 3), hash(n, 3), at(30));
            repository.save(session);
        });

        assertThat(List.of(added, refreshed, storedVersion(n))).containsExactly(0L, 1L, 2L);
    }

    @Test
    void GivenAStoredSession_WhenContactsAreRecordedAndDroppedAndSaved_ThenTheTimesAreKeptAndTheVersionStays() {
        int n = 9;
        transactions.inTransaction(() -> repository.add(opened(n)));

        List<Long> versions = new ArrayList<>();
        // The third contact comes under a minute after the second, so it keeps nothing.
        for (Instant contact : List.of(at(1), at(2), at(2).plusSeconds(30))) {
            transactions.inTransaction(() -> {
                Session session = repository.get(session(n)).orElseThrow();
                session.recordContact(contact);
                repository.save(session);
            });
            versions.add(storedVersion(n));
        }
        List<Instant> recorded = storedContacts(n);
        transactions.inTransaction(() -> {
            Session session = repository.get(session(n)).orElseThrow();
            session.dropOldContacts(at(1).plus(Session.CONTACT_RETENTION).plusSeconds(1));
            repository.save(session);
        });

        assertThat(versions).containsExactly(0L, 0L, 0L);
        assertThat(recorded).containsExactly(at(1), at(2));
        assertThat(storedContacts(n)).containsExactly(at(2));
        assertThat(storedVersion(n)).isZero();
    }

    @Test
    void GivenTwoTransactionsOnOneSession_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        int n = 10;
        transactions.inTransaction(() -> repository.add(opened(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Session mine = repository.get(session(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        Session theirs = repository.get(session(n)).orElseThrow();
                        theirs.exchange(hash(n, 1), FIRST, token(n, 2), hash(n, 2), at(20));
                        repository.save(theirs);
                    });
                    mine.confirmPassword(FIRST, token(n, 3), hash(n, 3), at(21));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Session");
                    assertThat(stale.id()).isEqualTo(session(n).value());
                });

        assertThat(storedVersion(n)).isEqualTo(1L);
        assertThat(storedTokenState(token(n, 2))).isEqualTo("CURRENT");
    }

    @Test
    void GivenAContactSavedWhileARefreshHeldAnOlderCopy_WhenTheRefreshSaves_ThenItSucceedsAndBothStand() {
        int n = 11;
        transactions.inTransaction(() -> repository.add(opened(n)));

        transactions.inTransaction(() -> {
            Session refreshing = repository.get(session(n)).orElseThrow();
            transactions.inANewTransaction(() -> {
                Session contacted = repository.get(session(n)).orElseThrow();
                contacted.recordContact(at(1));
                repository.save(contacted);
            });
            refreshing.exchange(hash(n, 1), FIRST, token(n, 2), hash(n, 2), at(2));
            repository.save(refreshing);
        });

        assertThat(storedVersion(n)).isEqualTo(1L);
        assertThat(storedContacts(n)).containsExactly(at(1));
        assertThat(storedTokenState(token(n, 2))).isEqualTo("CURRENT");
    }

    @Test
    void GivenARefreshSavedWhileAContactHeldAnOlderCopy_WhenTheContactSaves_ThenItSucceedsWithNoVersionCheck() {
        int n = 12;
        transactions.inTransaction(() -> repository.add(opened(n)));

        transactions.inTransaction(() -> {
            Session contacted = repository.get(session(n)).orElseThrow();
            transactions.inANewTransaction(() -> {
                Session refreshing = repository.get(session(n)).orElseThrow();
                refreshing.exchange(hash(n, 1), FIRST, token(n, 2), hash(n, 2), at(1));
                repository.save(refreshing);
            });
            contacted.recordContact(at(2));
            repository.save(contacted);
        });

        assertThat(storedVersion(n)).isEqualTo(1L);
        assertThat(storedContacts(n)).containsExactly(at(2));
        assertThat(storedTokenState(token(n, 2))).isEqualTo("CURRENT");
    }

    @Test
    void GivenTwoTransactionsEachRecordingAContact_WhenBothSave_ThenBothTimesAreKeptAndTheVersionStays() {
        int n = 24;
        transactions.inTransaction(() -> repository.add(opened(n)));

        transactions.inTransaction(() -> {
            Session mine = repository.get(session(n)).orElseThrow();
            transactions.inANewTransaction(() -> {
                Session theirs = repository.get(session(n)).orElseThrow();
                theirs.recordContact(at(1));
                repository.save(theirs);
            });
            // Neither copy saw the other's contact, so the spacing rule let both keep their own.
            mine.recordContact(at(1).plusSeconds(20));
            repository.save(mine);
        });

        assertThat(storedContacts(n)).containsExactly(at(1), at(1).plusSeconds(20));
        assertThat(storedVersion(n)).isZero();
    }

    @Test
    void
            GivenADropOfOldTimesSavedWhileAContactHeldAnOlderCopy_WhenTheContactSaves_ThenTheOldTimeGoesAndBothOthersStay() {
        int n = 25;
        Instant recent = T.plus(Duration.ofDays(7));
        Instant now = T.plus(Session.CONTACT_RETENTION).plusSeconds(1);
        transactions.inTransaction(() -> {
            Session session = opened(n);
            session.recordContact(T);
            session.recordContact(recent);
            repository.add(session);
        });

        transactions.inTransaction(() -> {
            Session contacted = repository.get(session(n)).orElseThrow();
            transactions.inANewTransaction(() -> {
                Session dropped = repository.get(session(n)).orElseThrow();
                dropped.dropOldContacts(now);
                repository.save(dropped);
            });
            // The contact drops the same old time from its own copy, a row already deleted.
            contacted.recordContact(now);
            repository.save(contacted);
        });

        assertThat(storedContacts(n)).containsExactly(recent, now);
        assertThat(storedVersion(n)).isZero();
    }

    // --- the revocation written with no version check (NFR-88, NFR-89)

    @Test
    void GivenALiveStoredSession_WhenItsRevocationIsSaved_ThenOnlyTheRevocationIsWrittenAndTheVersionMoves() {
        int n = 13;
        transactions.inTransaction(() -> repository.add(opened(n)));

        transactions.inTransaction(() -> {
            Session session = repository.get(session(n)).orElseThrow();
            session.recordContact(at(1));
            session.revoke(at(2));
            repository.saveRevocation(session);
        });

        Session stored =
                transactions.inTransaction(() -> repository.get(session(n)).orElseThrow());
        assertThat(stored.state()).isEqualTo(new SessionState.Revoked(at(2)));
        assertThat(stored.version()).isEqualTo(Version.of(1));
        assertThat(storedContacts(n)).isEmpty();
    }

    @Test
    void GivenARevocationAlreadyStored_WhenAnOlderCopysRevocationIsSaved_ThenTheStoredOneIsKept() {
        int n = 14;
        transactions.inTransaction(() -> repository.add(opened(n)));

        transactions.inTransaction(() -> {
            Session late = repository.get(session(n)).orElseThrow();
            transactions.inANewTransaction(() -> {
                Session first = repository.get(session(n)).orElseThrow();
                first.revoke(at(1));
                repository.saveRevocation(first);
            });
            late.revoke(at(2));
            repository.saveRevocation(late);
        });

        Session stored =
                transactions.inTransaction(() -> repository.get(session(n)).orElseThrow());
        assertThat(stored.state()).isEqualTo(new SessionState.Revoked(at(1)));
        assertThat(stored.version()).isEqualTo(Version.of(1));
    }

    @Test
    void GivenARevocationStoredAfterACopyWasRead_WhenThatCopyIsSavedLive_ThenStaleVersionAndTheRevocationStands() {
        int n = 15;
        transactions.inTransaction(() -> repository.add(opened(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Session refreshing = repository.get(session(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        Session signedOut = repository.get(session(n)).orElseThrow();
                        signedOut.revoke(at(1));
                        repository.saveRevocation(signedOut);
                    });
                    refreshing.exchange(hash(n, 1), FIRST, token(n, 2), hash(n, 2), at(2));
                    repository.save(refreshing);
                }))
                .isInstanceOf(StaleVersion.class);

        assertThat(transactions.inTransaction(
                        () -> repository.get(session(n)).orElseThrow().state()))
                .isEqualTo(new SessionState.Revoked(at(1)));
    }

    @Test
    void GivenALiveSession_WhenItIsSavedAsARevocation_ThenIllegalArgumentException() {
        int n = 16;
        transactions.inTransaction(() -> repository.add(opened(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() ->
                        repository.saveRevocation(repository.get(session(n)).orElseThrow())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storedVersion(n)).isZero();
    }

    // --- lookups

    @Test
    void GivenLiveRevokedAndUnknownSessions_WhenTheirLiveGenerationIsRead_ThenOnlyTheLiveOneHasOne() {
        int n = 17;
        transactions.inTransaction(() -> {
            Session stepped = opened(n);
            stepped.confirmPassword(FIRST, token(n, 2), hash(n, 2), at(1));
            repository.add(stepped);
            Session revoked = opened(100 + n);
            revoked.revoke(at(1));
            repository.add(revoked);
        });

        assertThat(transactions.inTransaction(() -> repository.liveGenerationOf(session(n))))
                .contains(SessionGeneration.of(1));
        assertThat(transactions.inTransaction(() -> repository.liveGenerationOf(session(100 + n))))
                .isEmpty();
        assertThat(transactions.inTransaction(() -> repository.liveGenerationOf(session(200 + n))))
                .isEmpty();
    }

    @Test
    void GivenAChainOfTokensInEveryState_WhenFoundByAnyTokensHash_ThenThatSessionWholeAndNoneForAnUnknownHash() {
        int n = 18;
        transactions.inTransaction(() -> {
            Session session = everyTokenState(n);
            session.recordContact(at(41));
            repository.add(session);
        });

        for (int m = 1; m <= 4; m++) {
            int tokenNumber = m;
            Session found = transactions.inTransaction(
                    () -> repository.withRefreshToken(hash(n, tokenNumber)).orElseThrow());
            assertThat(found.id()).isEqualTo(session(n));
            assertThat(found.contactTimes()).containsExactly(at(41));
            assertThat(found.tokens()).hasSize(4);
        }
        assertThat(transactions.inTransaction(() -> repository.withRefreshToken(hash(n, 9))))
                .isEmpty();
    }

    @Test
    void GivenAnAccountsSessionsAroundARevocation_WhenThoseItEndedAreListed_ThenTheLiveOnesAndThoseRevokedAfterIt() {
        int n = 19;
        Instant revokedAt = at(60);
        AccountGeneration revokedTo = AccountGeneration.of(2);
        transactions.inTransaction(() -> {
            repository.add(opened(n, n, FIRST, T));
            repository.add(revokedAt(100 + n, n, revokedAt.plusSeconds(1)));
            repository.add(revokedAt(200 + n, n, revokedAt.minusSeconds(1)));
            repository.add(revokedAt(300 + n, n, revokedAt));
            repository.add(opened(400 + n, n, revokedTo, T));
            repository.add(opened(500 + n, 500 + n, FIRST, T));
        });

        List<Session> ended =
                transactions.inTransaction(() -> repository.endedByRevocation(account(n), revokedTo, revokedAt));

        assertThat(ended).extracting(Session::id).containsExactlyInAnyOrder(session(n), session(100 + n));
        assertThat(ended).allSatisfy(session -> assertThat(session.tokens()).hasSize(1));
    }

    @Test
    void GivenLiveAndRevokedSessions_WhenTheLiveOnesAreListed_ThenOnlyTheLiveOnes() {
        int n = 20;
        transactions.inTransaction(() -> {
            repository.add(opened(n));
            repository.add(revokedAt(100 + n, 100 + n, at(1)));
        });
        Set<SessionId> ours = Set.of(session(n), session(100 + n));

        List<SessionId> live = transactions.inTransaction(() -> repository.allLive());

        assertThat(live).filteredOn(ours::contains).containsExactly(session(n));
    }

    @Test
    void GivenAnAccountsSessionsWithContacts_WhenItsContactsInASpanAreRead_ThenBothEndsAndRevokedSessionsInTimeOrder() {
        int n = 21;
        Instant from = at(10);
        Instant to = at(13);
        transactions.inTransaction(() -> {
            Session first = opened(n);
            first.recordContact(from.minusSeconds(60));
            first.recordContact(from);
            first.recordContact(at(12));
            repository.add(first);
            Session signedOut = opened(100 + n, n, FIRST, T);
            signedOut.recordContact(at(11));
            signedOut.recordContact(to);
            signedOut.recordContact(to.plusSeconds(60));
            signedOut.revoke(at(20));
            repository.add(signedOut);
            Session another = opened(200 + n, 200 + n, FIRST, T);
            another.recordContact(at(11));
            repository.add(another);
        });

        assertThat(transactions.inTransaction(() -> repository.contactsOf(account(n), from, to)))
                .containsExactly(from, at(11), at(12), to);
        assertThat(transactions.inTransaction(() -> repository.contactsOf(account(n), at(30), at(40))))
                .isEmpty();
    }

    // --- retention (NFR-258, NFR-280), in 2021

    @Test
    void GivenSessionsEndingAroundACutoff_WhenTheEndedOnesAreDeleted_ThenThoseEndedAtOrBeforeItGoWithTheirRows() {
        int n = 22;
        Instant cutoff = Instant.parse("2021-06-01T06:00:00Z");
        Duration day = Duration.ofDays(1);
        Duration lifetime = Session.REFRESH_TOKEN_LIFETIME;
        transactions.inTransaction(() -> {
            repository.add(revokedAt(n, n, cutoff.minusSeconds(1), cutoff.minus(day)));
            repository.add(revokedAt(100 + n, n, cutoff, cutoff.minus(day)));
            repository.add(revokedAt(200 + n, n, cutoff.plusSeconds(1), cutoff.minus(day)));
            repository.add(opened(300 + n, n, FIRST, cutoff.minus(lifetime)));
            repository.add(opened(400 + n, n, FIRST, cutoff.minus(lifetime).plusSeconds(1)));
            // Its token expired a day before the cutoff, though it was revoked a day after.
            Session expired = opened(500 + n, n, FIRST, cutoff.minus(lifetime).minus(day));
            expired.recordContact(cutoff.minus(lifetime));
            expired.revoke(cutoff.plus(day));
            repository.add(expired);
        });
        Set<SessionId> ours = Stream.of(0, 100, 200, 300, 400, 500)
                .map(offset -> session(offset + n))
                .collect(Collectors.toSet());

        List<SessionId> deleted = transactions.inTransaction(() -> repository.deleteEndedBefore(cutoff));

        assertThat(deleted)
                .filteredOn(ours::contains)
                .containsExactlyInAnyOrder(session(n), session(100 + n), session(300 + n), session(500 + n));
        assertThat(transactions.inTransaction(() -> repository.get(session(200 + n))))
                .isPresent();
        assertThat(transactions.inTransaction(() -> repository.get(session(400 + n))))
                .isPresent();
        assertThat(transactions.inTransaction(() -> repository.get(session(500 + n))))
                .isEmpty();
        assertThat(storedContacts(500 + n)).isEmpty();
        assertThat(storedTokens(500 + n)).isZero();
    }

    @Test
    void GivenContactsAroundACutoff_WhenTheSessionsWithOlderOnesAreListed_ThenOnlyThoseHoldingOneBeforeIt() {
        int n = 23;
        Instant cutoff = Instant.parse("2021-09-01T06:00:00Z");
        transactions.inTransaction(() -> {
            Session older = opened(n, n, FIRST, cutoff.minus(Duration.ofDays(1)));
            older.recordContact(cutoff.minusSeconds(1));
            older.recordContact(cutoff.plusSeconds(60));
            repository.add(older);
            Session atTheCutoff = opened(100 + n, n, FIRST, cutoff.minus(Duration.ofDays(1)));
            atTheCutoff.recordContact(cutoff);
            repository.add(atTheCutoff);
            repository.add(opened(200 + n, n, FIRST, cutoff.minus(Duration.ofDays(1))));
        });
        Set<SessionId> ours = Set.of(session(n), session(100 + n), session(200 + n));

        List<SessionId> due = transactions.inTransaction(() -> repository.withContactsBefore(cutoff));

        assertThat(due).filteredOn(ours::contains).containsExactly(session(n));
    }

    // --- a stored state the load check accepts and no command reaches

    /**
     * A revoked session whose last token is no longer current: no command leaves one, since a
     * revocation leaves the chain as it is, but the load check accepts at most one current token
     * on a revoked session, so the session's end is its revocation.
     */
    @Test
    void GivenAStoredRevokedSessionWithNoCurrentToken_WhenAskedWhenItEnded_ThenItIsTheRevocation() {
        int n = 26;
        transactions.inTransaction(() -> repository.add(Base.REVOKED_EXCHANGED.build(n)));
        new Rows(jdbc, n).setOnToken(2, "state = 'REPLACED', state_at = issued_at");

        Session stored =
                transactions.inTransaction(() -> repository.get(session(n)).orElseThrow());

        assertThat(stored.tokens()).noneMatch(token -> token.state() instanceof TokenState.Current);
        assertThat(stored.endedAt()).isEqualTo(at(30));
    }

    // --- the load check (ADR-127 decision 1)

    static Stream<Arguments> damagedRows() {
        String current = "a live session holds exactly one current token, a revoked one at most one";
        String successor = "an exchanged token's successor is another token of the chain";
        String nowCurrent = "state = 'CURRENT', state_at = NULL, successor = NULL";
        return Stream.of(
                Arguments.of(
                        "a session with no token",
                        1_001,
                        Base.ONE_TOKEN,
                        (Damage) Rows::deleteTheTokens,
                        "a session holds at least one refresh token"),
                Arguments.of(
                        "a live session with no current token",
                        1_004,
                        Base.ONE_TOKEN,
                        (Damage) rows -> rows.setOnToken(1, "state = 'SUPERSEDED', state_at = issued_at"),
                        current),
                Arguments.of(
                        "a live session with two current tokens",
                        1_005,
                        Base.EXCHANGED,
                        (Damage) rows -> rows.setOnToken(1, nowCurrent),
                        current),
                Arguments.of(
                        "a revoked session with two current tokens",
                        1_006,
                        Base.REVOKED_EXCHANGED,
                        (Damage) rows -> rows.setOnToken(1, nowCurrent),
                        current),
                Arguments.of(
                        "an exchanged token whose successor is not in the chain",
                        1_007,
                        Base.EXCHANGED,
                        (Damage) rows -> rows.setOnToken(
                                1, "successor = ?", token(1_007, 9).value()),
                        successor),
                Arguments.of(
                        "an exchanged token that is its own successor",
                        1_008,
                        Base.EXCHANGED,
                        (Damage) rows -> rows.setOnToken(1, "successor = id"),
                        successor));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredSessionBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Base base, Damage damage, String detail) {
        transactions.inTransaction(() -> repository.add(base.build(n)));

        damage.apply(new Rows(jdbc, n));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(session(n))))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("Session");
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("two tokens with one ID", 1_002, (Damage)
                        rows -> rows.setOnToken(2, "id = ?", token(1_002, 1).value())),
                Arguments.of("an exchanged token naming no successor", 1_010, (Damage)
                        rows -> rows.setOnToken(1, "successor = NULL")),
                Arguments.of("a revoked session with no revocation time", 1_011, (Damage)
                        rows -> rows.setOnSession("state = 'REVOKED'")),
                Arguments.of("one contact time twice", 1_012, (Damage) Rows::insertOneContactTimeTwice),
                Arguments.of("two tokens with one hash", 1_003, (Damage) rows ->
                        rows.setOnToken(2, "token_hash = ?", hash(1_003, 1).value())),
                Arguments.of("a token with another session's hash", 1_013, (Damage) rows ->
                        rows.setOnToken(2, "token_hash = ?", hash(1_113, 1).value())));
    }

    /** Session {@code 100 + n}, beside the base, holds the token hash a case takes. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredSession_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Damage damage) {
        transactions.inTransaction(() -> {
            repository.add(Base.EXCHANGED.build(n));
            repository.add(Base.ONE_TOKEN.build(100 + n));
        });
        Rows rows = new Rows(jdbc, n);

        assertThatThrownBy(() -> damage.apply(rows)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(session(n)))).isPresent();
    }

    /** The valid states the damage cases start from. */
    enum Base {
        ONE_TOKEN,
        /** Token 1 exchanged for token 2, which is current. */
        EXCHANGED,
        REVOKED_EXCHANGED;

        Session build(int n) {
            return switch (this) {
                case ONE_TOKEN -> opened(n);
                case EXCHANGED -> exchanged(n);
                case REVOKED_EXCHANGED -> {
                    Session session = exchanged(n);
                    session.revoke(at(30));
                    yield session;
                }
            };
        }
    }

    /** One damage done with SQL to a stored session's rows. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** The SQL the damage cases run on session {@code n}'s rows. */
    record Rows(JdbcClient jdbc, int n) {

        /** Sets columns of the session; the assignments are the test's own constant SQL. */
        void setOnSession(String assignments) {
            jdbc.sql("UPDATE access.session SET " + assignments + " WHERE id = ?")
                    .param(session(n).value())
                    .update();
        }

        /** Sets columns of token {@code m}; the assignments are the test's own constant SQL. */
        void setOnToken(int m, String assignments, Object... values) {
            List<Object> params = new ArrayList<>(List.of(values));
            params.add(token(n, m).value());
            jdbc.sql("UPDATE access.refresh_token SET " + assignments + " WHERE id = ?")
                    .params(params)
                    .update();
        }

        void deleteTheTokens() {
            jdbc.sql("DELETE FROM access.refresh_token WHERE session_id = ?")
                    .param(session(n).value())
                    .update();
        }

        /** Stores one instant twice among the session's contact times, in one statement. */
        void insertOneContactTimeTwice() {
            OffsetDateTime contact = at(1).atOffset(ZoneOffset.UTC);
            jdbc.sql("INSERT INTO access.session_contact (session_id, contacted_at) VALUES (?, ?), (?, ?)")
                    .params(session(n).value(), contact, session(n).value(), contact)
                    .update();
        }
    }

    // --- states, reached through the domain's commands

    /** Session {@code n} of account {@code n}, signed in at {@link #T} under generation 1. */
    private static Session opened(int n) {
        return opened(n, n, FIRST, T);
    }

    /** Session {@code n} of account {@code a}, signed in at {@code at} under a generation. */
    private static Session opened(int n, int a, AccountGeneration generation, Instant at) {
        return Session.open(session(n), signedInAccount(a, generation), token(n, 1), hash(n, 1), at);
    }

    /** Session {@code n} of account {@code a}, signed in at {@link #T} and revoked at {@code at}. */
    private static Session revokedAt(int n, int a, Instant at) {
        return revokedAt(n, a, at, T);
    }

    private static Session revokedAt(int n, int a, Instant at, Instant openedAt) {
        Session session = opened(n, a, FIRST, openedAt);
        session.revoke(at);
        return session;
    }

    /** Token 1 exchanged for token 2 at 20 minutes. */
    private static Session exchanged(int n) {
        Session session = opened(n);
        RefreshOutcome outcome = session.exchange(hash(n, 1), FIRST, token(n, 2), hash(n, 2), at(20));
        if (!(outcome instanceof RefreshOutcome.Issued)) {
            throw new AssertionError("the first exchange issues a token");
        }
        return session;
    }

    /**
     * Token 1 exchanged and presented again within the grace, which replaced token 2 with token 3;
     * then a step-up superseded token 3 with token 4.
     */
    private static Session everyTokenState(int n) {
        Session session = exchanged(n);
        session.exchange(hash(n, 1), FIRST, token(n, 3), hash(n, 3), at(20).plusSeconds(5));
        session.confirmPassword(FIRST, token(n, 4), hash(n, 4), at(40));
        return session;
    }

    /**
     * An account that may sign in, at a generation of 1 or more: each password change moves it by
     * one. It is never stored: a session keeps only its account's ID.
     */
    private static UserAccount signedInAccount(int a, AccountGeneration generation) {
        EmailAddress email = EmailAddress.parse("driver" + a + "@session-it.example");
        UserAccount account = UserAccount.open(
                account(a), email, RoleSet.of(Role.DRIVER), Language.HEBREW, EmailHolder.nobody(email));
        for (long g = 0; g < generation.value(); g++) {
            account.changePassword(new PasswordHash("hash-" + a), T);
        }
        return account;
    }

    // --- keys, times and stored values

    private static SessionId session(int n) {
        return SessionId.of(new UUID(KEY_PREFIX, n));
    }

    private static RefreshTokenId token(int n, int m) {
        return RefreshTokenId.of(new UUID(KEY_PREFIX + 1, 100L * n + m));
    }

    private static AccountId account(int a) {
        return AccountId.of(new UUID(KEY_PREFIX + 2, a));
    }

    private static TokenHash hash(int n, int m) {
        return new TokenHash("refresh-" + n + "-" + m);
    }

    private static Instant at(int minutesLater) {
        return T.plus(Duration.ofMinutes(minutesLater));
    }

    private long storedVersion(int n) {
        return jdbc.sql("SELECT version FROM access.session WHERE id = ?")
                .param(session(n).value())
                .query(Long.class)
                .single();
    }

    private List<Instant> storedContacts(int n) {
        return jdbc.sql("SELECT contacted_at FROM access.session_contact WHERE session_id = ? ORDER BY contacted_at")
                .param(session(n).value())
                .query((row, index) -> row.getObject(1, OffsetDateTime.class).toInstant())
                .list();
    }

    private long storedTokens(int n) {
        return jdbc.sql("SELECT count(*) FROM access.refresh_token WHERE session_id = ?")
                .param(session(n).value())
                .query(Long.class)
                .single();
    }

    private String storedTokenState(RefreshTokenId token) {
        return jdbc.sql("SELECT state FROM access.refresh_token WHERE id = ?")
                .param(token.value())
                .query(String.class)
                .single();
    }
}
