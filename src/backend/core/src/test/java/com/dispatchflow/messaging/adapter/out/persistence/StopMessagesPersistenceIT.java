package com.dispatchflow.messaging.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.messaging.domain.CompanyName;
import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.messaging.domain.MessageLanguage;
import com.dispatchflow.messaging.domain.MessagingRetention;
import com.dispatchflow.messaging.domain.OutgoingMessageId;
import com.dispatchflow.messaging.domain.PhoneConsent;
import com.dispatchflow.messaging.domain.ProjectedArrival;
import com.dispatchflow.messaging.domain.SendingConditions;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.messaging.domain.message.ArrivalFacts;
import com.dispatchflow.messaging.domain.message.ArrivalFacts.PreviousStop;
import com.dispatchflow.messaging.domain.message.DailySendCap;
import com.dispatchflow.messaging.domain.message.MessageDescription;
import com.dispatchflow.messaging.domain.message.ProviderReference;
import com.dispatchflow.messaging.domain.message.RecipientPhone;
import com.dispatchflow.messaging.domain.message.ReportedOutcome;
import com.dispatchflow.messaging.domain.message.RouteProgress;
import com.dispatchflow.messaging.domain.message.StopMessages;
import com.dispatchflow.messaging.domain.message.StopMessages.ProviderRepeats;
import com.dispatchflow.messaging.domain.message.StopProgress;
import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * {@link StopMessagesRepository} against a real PostgreSQL (ADR-126, ADR-127): what a stop's
 * messages store and load as, the version, the claimable stops, the daily cap and its lock, the
 * last acceptance to a phone, retention, and the stored states the load check refuses.
 *
 * <p>The database is shared and never cleaned, and the cap, the claimable stops and retention read
 * every stop. So test {@code n} keys its own stops ({@code stop(n)}, {@code stop(100 + n)}, …) and
 * phones, and works on its own Israel day, {@code dayOf(n)}, whose neighbours no test uses.
 * Message {@code m} of stop {@code s} is {@code messageId(s, m)}.
 */
@IntegrationTest
class StopMessagesPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_6d65_7373L;
    private static final LocalDate FIRST_DAY = LocalDate.of(2030, 1, 1);
    private static final int DAYS_APART = 3;
    private static final LocalTime TEN = LocalTime.of(10, 0);
    private static final CompanyName COMPANY = CompanyName.of("Shefa Deliveries");
    private static final DispatchPhone DISPATCH = DispatchPhone.of(PhoneNumber.ofStored("+97235551234"));
    private static final SendingConditions MESSAGES_ON = conditions(Switch.OFF);
    private static final SendingConditions RATING_QUESTION_ON = conditions(Switch.ON);
    private static final DailySendCap ROOM = new DailySendCap(0);
    private static final ProviderReference REFERENCE = new ProviderReference("provider-1");

    @Autowired
    private StopMessagesRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    // --- round trips (ADR-127 decision 1)

    /** The states a stop's messages store and load in, reached through commands. */
    enum Scenario {
        NO_MESSAGE,
        ONE_PENDING,
        EVERY_STATE,
        TO_TWO_PHONES
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenAStopsMessages_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(Scenario scenario) {
        int n = 20 + scenario.ordinal();
        StopMessages added = transactions.inTransaction(() -> {
            StopMessages messages = switch (scenario) {
                case NO_MESSAGE -> StopMessages.open(stop(n));
                case ONE_PENDING -> pendingOutForDelivery(n, tenOn(n), phone(n));
                case EVERY_STATE -> everyState(n, tenOn(n), phone(n));
                case TO_TWO_PHONES -> toTwoPhones(n, tenOn(n));
            };
            repository.add(messages);
            return messages;
        });

        StopMessages loaded =
                transactions.inTransaction(() -> repository.get(stop(n)).orElseThrow());

        assertThat(MessageDescription.everyMessageOf(loaded)).isEqualTo(MessageDescription.everyMessageOf(added));
        assertThat(loaded.stop()).isEqualTo(stop(n));
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.retentionEndsAt()).isEqualTo(added.retentionEndsAt());
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoTriggerForAStop_WhenItsMessagesAreLoaded_ThenThereAreNone() {
        assertThat(transactions.inTransaction(() -> repository.get(stop(4)))).isEmpty();
    }

    // --- versions and conflicts

    @Test
    void GivenAddedMessages_WhenAMessageIsClaimedAndThenAccepted_ThenTheVersionIsZeroThenOneThenTwo() {
        int n = 5;
        Instant ten = tenOn(n);
        transactions.inTransaction(() -> repository.add(pendingOutForDelivery(n, ten, phone(n))));
        long added = storedVersion(n);

        transactions.inTransaction(() -> {
            StopMessages messages = repository.get(stop(n)).orElseThrow();
            messages.claim(messageId(n, 1), ROOM, PhoneConsent.MAY_BE_MESSAGED, at(ten, 1));
            repository.save(messages);
        });
        long claimed = storedVersion(n);
        transactions.inTransaction(() -> {
            StopMessages messages = repository.get(stop(n)).orElseThrow();
            messages.accepted(messageId(n, 1), REFERENCE, at(ten, 2));
            repository.save(messages);
        });

        assertThat(List.of(added, claimed, storedVersion(n))).containsExactly(0L, 1L, 2L);
    }

    @Test
    void GivenTwoTransactionsOnOneStop_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        int n = 6;
        Instant ten = tenOn(n);
        transactions.inTransaction(() -> repository.add(pendingOutForDelivery(n, ten, phone(n))));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    StopMessages mine = repository.get(stop(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        StopMessages theirs = repository.get(stop(n)).orElseThrow();
                        theirs.claim(messageId(n, 1), ROOM, PhoneConsent.MAY_BE_MESSAGED, at(ten, 1));
                        repository.save(theirs);
                    });
                    mine.claim(messageId(n, 1), ROOM, PhoneConsent.OPTED_OUT, at(ten, 1));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("StopMessages");
                    assertThat(stale.id()).isEqualTo(stop(n).value());
                });

        assertThat(storedVersion(n)).isEqualTo(1L);
        assertThat(storedState(messageId(n, 1))).isEqualTo("SENDING");
    }

    /**
     * Both triggers number their message the same, so the later one's message breaks the stored
     * messages' uniqueness by stop, type and sequence before its version is checked.
     */
    @Test
    void GivenTwoTriggersAddingOneMessageTypeToOneStop_WhenTheLaterOneSaves_ThenStaleVersionAndOnlyTheFirstIsStored() {
        int n = 40;
        Instant ten = tenOn(n);
        RecipientPhone to = new RecipientPhone.Known(phone(n));
        transactions.inTransaction(() -> repository.add(pendingOutForDelivery(n, ten, phone(n))));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    StopMessages mine = repository.get(stop(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        StopMessages theirs = repository.get(stop(n)).orElseThrow();
                        theirs.onDeliveryChanged(
                                messageId(n, 2),
                                to,
                                MESSAGES_ON,
                                RouteProgress.IN_PROGRESS,
                                StopProgress.NOT_REACHED,
                                at(ten, 1));
                        repository.save(theirs);
                    });
                    mine.onDeliveryChanged(
                            messageId(n, 3),
                            to,
                            MESSAGES_ON,
                            RouteProgress.IN_PROGRESS,
                            StopProgress.NOT_REACHED,
                            at(ten, 1));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("StopMessages");
                    assertThat(stale.id()).isEqualTo(stop(n).value());
                });

        assertThat(storedVersion(n)).isEqualTo(1L);
        assertThat(storedState(messageId(n, 2))).isEqualTo("PENDING");
        assertThat(storedMessages(n)).isEqualTo(2L);
    }

    /**
     * The trigger adds a message to the root's own list, so the flush that the day's cap runs moves
     * the root's version before the save, which then has no step of its own to take.
     */
    @Test
    void GivenATriggerOnStoredMessages_WhenTheDaysCapIsReadBeforeTheSave_ThenAssertionErrorAndNothingIsStored() {
        int n = 41;
        Instant ten = tenOn(n);
        RecipientPhone to = new RecipientPhone.Known(phone(n));
        transactions.inTransaction(() -> repository.add(pendingOutForDelivery(n, ten, phone(n))));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    StopMessages messages = repository.get(stop(n)).orElseThrow();
                    messages.onDeliveryChanged(
                            messageId(n, 2),
                            to,
                            MESSAGES_ON,
                            RouteProgress.IN_PROGRESS,
                            StopProgress.NOT_REACHED,
                            at(ten, 1));
                    repository.capOn(dayOf(n));
                    repository.save(messages);
                }))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("a save must move the version");

        assertThat(storedVersion(n)).isZero();
        assertThat(storedMessages(n)).isEqualTo(1L);
    }

    @Test
    void GivenAnotherTriggerStoredTheStopsMessagesFirst_WhenTheyAreAdded_ThenStaleVersionAndTheFirstStands() {
        int n = 7;
        Instant ten = tenOn(n);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    StopMessages mine = pendingOutForDelivery(n, ten, phone(n));
                    transactions.inANewTransaction(() -> repository.add(deliveryChanged(n, ten, phone(n))));
                    repository.add(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("StopMessages");
                    assertThat(stale.id()).isEqualTo(stop(n).value());
                });

        assertThat(storedState(messageId(n, 2))).isEqualTo("PENDING");
        assertThat(storedMessages(n)).isEqualTo(1L);
    }

    // --- the stops the sender may claim from (DL §9.5)

    @Test
    void GivenStopsWithClaimableAndEndedMessages_WhenTheClaimableStopsAreListed_ThenTheOldestTriggerComesFirst() {
        int n = 8;
        int pendingLater = n;
        int unknownEarlier = 100 + n;
        int acceptedOnly = 200 + n;
        Instant ten = tenOn(n);
        transactions.inTransaction(() -> {
            repository.add(pendingOutForDelivery(pendingLater, at(ten, 5), phone(n)));
            StopMessages unknown = claimed(unknownEarlier, ten, phone(n));
            unknown.outcomeUnknown(messageId(unknownEarlier, 1), ProviderRepeats.DROPPED_BY_REFERENCE, at(ten, 2));
            repository.add(unknown);
            StopMessages accepted = claimed(acceptedOnly, at(ten, -1), phone(n));
            accepted.accepted(messageId(acceptedOnly, 1), REFERENCE, at(ten, 1));
            repository.add(accepted);
        });
        Set<StopId> ours = Set.of(stop(pendingLater), stop(unknownEarlier), stop(acceptedOnly));

        List<StopId> claimable = transactions.inTransaction(() -> repository.withPendingMessages());

        assertThat(claimable).filteredOn(ours::contains).containsExactly(stop(unknownEarlier), stop(pendingLater));
    }

    // --- the daily cap (NFR-196)

    @Test
    void GivenMessagesInEveryStateAndCountedAnswers_WhenTheDaysCapIsRead_ThenItCountsWhatMayHaveLeftThatDay() {
        int n = 9;
        LocalDate day = dayOf(n);
        Duration oneDay = Duration.ofDays(1);
        transactions.inTransaction(() -> {
            // Five of these six count: three accepted, one of unknown outcome and one sending.
            repository.add(everyState(n, tenOn(n), phone(n)));
            repository.add(pendingOutForDelivery(100 + n, tenOn(n), phone(n)));
            repository.add(claimed(200 + n, tenOn(n).minus(oneDay), phone(n)));
            repository.add(claimed(300 + n, tenOn(n).plus(oneDay), phone(n)));
            repository.countAnswer(answerId(n, 1), day);
            repository.countAnswer(answerId(n, 2), day);
            repository.countAnswer(answerId(n, 3), day.plusDays(1));
        });

        assertThat(transactions.inTransaction(() -> repository.capOn(day))).isEqualTo(new DailySendCap(7));
    }

    @Test
    void GivenADayWithNoMessageAndNoAnswer_WhenItsCapIsRead_ThenNothingCounts() {
        assertThat(transactions.inTransaction(() -> repository.capOn(dayOf(10))))
                .isEqualTo(new DailySendCap(0));
    }

    @Test
    void GivenACapWithRoom_WhenOneAnswerIsCountedTwice_ThenItCountsOnceAndBothCallsAnswerThatItMayBeSent() {
        int n = 11;
        LocalDate day = dayOf(n);

        boolean first = transactions.inTransaction(() -> repository.countAnswer(answerId(n, 1), day));
        boolean again = transactions.inTransaction(() -> repository.countAnswer(answerId(n, 1), day));

        assertThat(List.of(first, again)).containsExactly(true, true);
        assertThat(transactions.inTransaction(() -> repository.capOn(day))).isEqualTo(new DailySendCap(1));
    }

    @Test
    void GivenACapOneBelowItsLimit_WhenTwoAnswersAreCounted_ThenTheFirstTakesTheLastPlaceAndTheSecondIsRefused() {
        int n = 12;
        LocalDate day = dayOf(n);
        storeCountedAnswers(n, day, DailySendCap.LIMIT - 1);

        boolean last = transactions.inTransaction(() -> repository.countAnswer(answerId(n, 1), day));
        boolean over = transactions.inTransaction(() -> repository.countAnswer(answerId(n, 2), day));
        boolean overAgain = transactions.inTransaction(() -> repository.countAnswer(answerId(n, 2), day));

        assertThat(List.of(last, over, overAgain)).containsExactly(true, false, false);
        assertThat(transactions.inTransaction(() -> repository.capOn(day)))
                .isEqualTo(new DailySendCap(DailySendCap.LIMIT));
    }

    @Test
    void GivenAnAnswerCountedBeforeTheCapFilled_WhenItIsCountedAgain_ThenItStillMayBeSent() {
        int n = 13;
        LocalDate day = dayOf(n);
        transactions.inTransaction(() -> repository.countAnswer(answerId(n, 1), day));
        storeCountedAnswers(n, day, DailySendCap.LIMIT - 1);

        assertThat(transactions.inTransaction(() -> repository.countAnswer(answerId(n, 1), day)))
                .isTrue();
        assertThat(transactions.inTransaction(() -> repository.capOn(day)))
                .isEqualTo(new DailySendCap(DailySendCap.LIMIT));
    }

    @Test
    void GivenATransactionHoldingADaysCap_WhenAnotherReadsThatCapOrCountsAnAnswer_ThenItCannotTakeTheDaysLock() {
        int n = 14;
        LocalDate day = dayOf(n);

        transactions.inTransaction(() -> {
            repository.capOn(day);
            assertThatThrownBy(() -> transactions.inANewTransaction(() -> {
                        shortLockTimeout();
                        repository.capOn(day);
                    }))
                    .isInstanceOf(PessimisticLockingFailureException.class);
            assertThatThrownBy(() -> transactions.inANewTransaction(() -> {
                        shortLockTimeout();
                        repository.countAnswer(answerId(n, 1), day);
                    }))
                    .isInstanceOf(PessimisticLockingFailureException.class);
            transactions.inANewTransaction(() -> {
                shortLockTimeout();
                assertThat(repository.capOn(day.plusDays(1))).isEqualTo(new DailySendCap(0));
            });
        });

        assertThat(transactions.inTransaction(() -> repository.capOn(day))).isEqualTo(new DailySendCap(0));
    }

    /** A lock taken in a transaction of the call's own would be gone before the caller acts. */
    @Test
    void GivenNoTransaction_WhenADaysCapIsRead_ThenTheCallIsRefused() {
        assertThatThrownBy(() -> repository.capOn(dayOf(15))).isInstanceOf(IllegalTransactionStateException.class);
    }

    // --- the last acceptance to a phone (FR-363)

    @Test
    void GivenMessagesToAPhoneAndToAnother_WhenItsLastAcceptanceIsRead_ThenItIsTheLatestAcceptanceToThatPhone() {
        int n = 15;
        Instant ten = tenOn(n);
        transactions.inTransaction(() -> {
            // Accepted at 10:02, 10:07 and 10:12.
            repository.add(everyState(n, ten, phone(n)));
            StopMessages later = claimed(100 + n, at(ten, 60), phone(100 + n));
            later.accepted(messageId(100 + n, 1), REFERENCE, at(ten, 62));
            repository.add(later);
        });

        assertThat(transactions.inTransaction(() -> repository.lastAcceptedTo(phone(n))))
                .contains(at(ten, 12));
    }

    @Test
    void GivenAPhoneWhoseMessagesWereNeverAccepted_WhenItsLastAcceptanceIsRead_ThenThereIsNone() {
        int n = 16;
        transactions.inTransaction(() -> repository.add(claimed(n, tenOn(n), phone(n))));

        assertThat(transactions.inTransaction(() -> repository.lastAcceptedTo(phone(n))))
                .isEmpty();
        assertThat(transactions.inTransaction(() -> repository.lastAcceptedTo(phone(100 + n))))
                .isEmpty();
    }

    // --- retention (NFR-184, ADR-108)

    @Test
    void GivenStopsWhoseRetentionEndsAtDifferentTimes_WhenExpiredOnesAreDeleted_ThenOnlyThoseWhoseEndCameGo() {
        int expiring = 17;
        int dayLater = 117;
        int noMessage = 217;
        // In 2020, before every other test's day. 90 Israel days from 10 January cross the spring
        // clock change, so the retention ends an hour before 90 days of 24 hours have passed.
        Instant lastState = LocalDate.of(2020, 1, 10)
                .atTime(TEN)
                .atZone(IsraelDateTime.ZONE)
                .toInstant();
        Instant end = MessagingRetention.endOf(lastState);
        transactions.inTransaction(() -> {
            repository.add(pendingOutForDelivery(expiring, lastState, phone(expiring)));
            repository.add(pendingOutForDelivery(dayLater, lastState.plus(Duration.ofDays(1)), phone(expiring)));
            repository.add(StopMessages.open(stop(noMessage)));
        });

        int beforeTheEnd = transactions.inTransaction(() -> deleteExpiredInAUtcSession(end.minusSeconds(1)));
        int atTheEnd = transactions.inTransaction(() -> deleteExpiredInAUtcSession(end));

        assertThat(end).isEqualTo(lastState.plus(Duration.ofDays(90)).minus(Duration.ofHours(1)));
        assertThat(List.of(beforeTheEnd, atTheEnd)).containsExactly(0, 1);
        assertThat(transactions.inTransaction(() -> repository.get(stop(expiring))))
                .isEmpty();
        assertThat(storedMessages(expiring)).isZero();
        assertThat(transactions.inTransaction(() -> repository.get(stop(dayLater))))
                .isPresent();
        assertThat(transactions.inTransaction(() -> repository.get(stop(noMessage))))
                .isPresent();
    }

    // --- the load check (ADR-127 decision 1), mirroring the domain's damaged snapshots

    static Stream<Arguments> damagedRows() {
        String numbering = "each type is numbered from 1, up to its most per stop (NFR-191, FR-384)";
        String arrival = "only out for delivery and time changed give an arrival";
        return Stream.of(
                Arguments.of(
                        "a time changed numbered 2 with no 1",
                        1_001,
                        Base.TIME_CHANGED,
                        (Damage) rows -> rows.update(2, "sequence = 2"),
                        numbering),
                Arguments.of(
                        "a third time changed",
                        1_002,
                        Base.TWO_TIME_CHANGES,
                        (Damage) rows -> rows.insertAccepted(9, 4, "TIME_CHANGED", 3, true),
                        numbering),
                Arguments.of(
                        "an arriving soon that gives an arrival",
                        1_003,
                        Base.ARRIVING_SOON,
                        (Damage) rows -> rows.update(2, "arrival_sent = triggered_at"),
                        arrival),
                Arguments.of(
                        "an out for delivery with no arrival",
                        1_004,
                        Base.OUT_FOR_DELIVERY,
                        (Damage) rows -> rows.update(1, "arrival_sent = NULL"),
                        arrival),
                Arguments.of(
                        "a missed you that asks for a rating",
                        1_005,
                        Base.MISSED_YOU,
                        (Damage) rows -> rows.update(2, "asks_for_rating = true"),
                        "only a delivered message asks for a rating"),
                Arguments.of(
                        "a time changed before any arrival",
                        1_006,
                        Base.TIME_CHANGED,
                        (Damage) Rows::swapTheFirstTwo,
                        "a time changed message follows one that gave an arrival (FR-353)"),
                Arguments.of(
                        "a pending message pending since another time",
                        1_007,
                        Base.OUT_FOR_DELIVERY,
                        (Damage) rows -> rows.update(1, "state_since = triggered_at + interval '1 minute'"),
                        "a pending message is pending since its trigger"),
                Arguments.of(
                        "both outcome messages",
                        1_008,
                        Base.DELIVERED,
                        (Damage) rows -> rows.insertAccepted(9, 2, "MISSED_YOU", 1, false),
                        "a stop gets one outcome message, for its first outcome (FR-374)"),
                Arguments.of(
                        "messages whose positions start past the first",
                        1_012,
                        Base.TIME_CHANGED,
                        (Damage) Rows::shiftPositions,
                        StoredLists.GAP));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenStoredMessagesBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Base base, Damage damage, String detail) {
        transactions.inTransaction(() -> repository.add(base.build(n, tenOn(n), phone(n))));

        damage.apply(new Rows(jdbc, n));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(stop(n))))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("StopMessages");
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("two messages with one ID", 1_009, (Damage)
                        rows -> rows.update(2, "id = '" + messageId(1_009, 1).value() + "'")),
                Arguments.of("a second out for delivery", 1_010, (Damage)
                        rows -> rows.update(2, "type = 'OUT_FOR_DELIVERY'")),
                Arguments.of("a message with no phone", 1_011, (Damage) rows -> rows.update(1, "phone = NULL")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenStoredMessages_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Damage damage) {
        transactions.inTransaction(() -> repository.add(Base.ARRIVING_SOON.build(n, tenOn(n), phone(n))));
        Rows rows = new Rows(jdbc, n);

        assertThatThrownBy(() -> damage.apply(rows)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(stop(n)))).isPresent();
    }

    /** The valid states the damage cases start from: message 1 is "out for delivery", pending. */
    enum Base {
        OUT_FOR_DELIVERY,
        ARRIVING_SOON,
        TIME_CHANGED,
        TWO_TIME_CHANGES,
        MISSED_YOU,
        DELIVERED;

        StopMessages build(int s, Instant ten, PhoneNumber phone) {
            StopMessages messages = pendingOutForDelivery(s, ten, phone);
            RecipientPhone to = new RecipientPhone.Known(phone);
            switch (this) {
                case OUT_FOR_DELIVERY -> {}
                // 10:45 at 10:30: 15 minutes away, and 15 from the 11:00 sent, so arriving soon alone.
                case ARRIVING_SOON ->
                    messages.onArrivalProjected(ids(s, 2), to, MESSAGES_ON, inProgress(at(ten, 45)), at(ten, 30));
                // 11:40 at 10:05: 40 minutes from the 11:00 sent, so time changed alone.
                case TIME_CHANGED ->
                    messages.onArrivalProjected(ids(s, 2), to, MESSAGES_ON, inProgress(at(ten, 100)), at(ten, 5));
                case TWO_TIME_CHANGES -> {
                    messages.onArrivalProjected(ids(s, 2), to, MESSAGES_ON, inProgress(at(ten, 100)), at(ten, 5));
                    messages.onArrivalProjected(ids(s, 3, 4), to, MESSAGES_ON, inProgress(at(ten, 20)), at(ten, 10));
                }
                case MISSED_YOU ->
                    messages.onOutcome(messageId(s, 2), to, MESSAGES_ON, new ReportedOutcome.Failed(), ten);
                case DELIVERED ->
                    messages.onOutcome(messageId(s, 2), to, MESSAGES_ON, new ReportedOutcome.Delivered(false), ten);
            }
            return messages;
        }
    }

    /** One damage done with SQL to a stored stop's messages. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** The SQL the damage cases run on stop {@code s}'s messages. */
    record Rows(JdbcClient jdbc, int s) {

        /** Sets columns of message {@code m}; the assignments are the test's own constant SQL. */
        void update(int m, String assignments) {
            jdbc.sql("UPDATE messaging.outgoing_message SET " + assignments + " WHERE id = ?")
                    .param(messageId(s, m).value())
                    .update();
        }

        /** Puts the first message second and the second first. */
        void swapTheFirstTwo() {
            jdbc.sql("UPDATE messaging.outgoing_message SET position = 1 - position WHERE stop_messages_id = ?")
                    .param(stop(s).value())
                    .update();
        }

        /** Moves every message one position on, so no message holds the first. */
        void shiftPositions() {
            jdbc.sql("UPDATE messaging.outgoing_message SET position = position + 1 WHERE stop_messages_id = ?")
                    .param(stop(s).value())
                    .update();
        }

        /** Adds message {@code m}, accepted, copying message 1's phone, text and trigger. */
        void insertAccepted(int m, int position, String type, int sequence, boolean givesAnArrival) {
            jdbc.sql("""
                            INSERT INTO messaging.outgoing_message
                                (id, stop_messages_id, position, type, sequence, phone, text, arrival_sent,
                                 asks_for_rating, triggered_at, state, state_since, provider_reference)
                            SELECT ?, stop_messages_id, ?, ?, ?, phone, text, CASE WHEN ? THEN arrival_sent END,
                                   false, triggered_at, 'ACCEPTED', triggered_at, 'provider-damage'
                            FROM messaging.outgoing_message WHERE id = ?
                            """)
                    .params(
                            messageId(s, m).value(),
                            position,
                            type,
                            sequence,
                            givesAnArrival,
                            messageId(s, 1).value())
                    .update();
        }
    }

    // --- states, reached through the domain's commands

    /**
     * Six messages in every state but pending: "out for delivery" and two "time changed" accepted,
     * "arriving soon" of unknown outcome, "delivery changed" sending, and "delivered", which asks
     * for a rating, dropped because its phone opted out.
     */
    private static StopMessages everyState(int s, Instant ten, PhoneNumber phone) {
        RecipientPhone to = new RecipientPhone.Known(phone);
        StopMessages messages = claimed(s, ten, phone);
        messages.accepted(messageId(s, 1), REFERENCE, at(ten, 2));
        messages.onArrivalProjected(ids(s, 2), to, MESSAGES_ON, inProgress(at(ten, 100)), at(ten, 5));
        messages.claim(messageId(s, 2), ROOM, PhoneConsent.MAY_BE_MESSAGED, at(ten, 6));
        messages.accepted(messageId(s, 2), new ProviderReference("provider-2"), at(ten, 7));
        messages.onArrivalProjected(ids(s, 3, 4), to, MESSAGES_ON, inProgress(at(ten, 20)), at(ten, 10));
        messages.claim(messageId(s, 3), ROOM, PhoneConsent.MAY_BE_MESSAGED, at(ten, 11));
        messages.outcomeUnknown(messageId(s, 3), ProviderRepeats.DROPPED_BY_REFERENCE, at(ten, 12));
        messages.claim(messageId(s, 4), ROOM, PhoneConsent.MAY_BE_MESSAGED, at(ten, 11));
        messages.accepted(messageId(s, 4), new ProviderReference("provider-4"), at(ten, 12));
        messages.onDeliveryChanged(
                messageId(s, 5), to, MESSAGES_ON, RouteProgress.IN_PROGRESS, StopProgress.NOT_REACHED, at(ten, 13));
        messages.claim(messageId(s, 5), ROOM, PhoneConsent.MAY_BE_MESSAGED, at(ten, 14));
        messages.onOutcome(messageId(s, 6), to, RATING_QUESTION_ON, new ReportedOutcome.Delivered(true), at(ten, 15));
        messages.claim(messageId(s, 6), ROOM, PhoneConsent.OPTED_OUT, at(ten, 16));
        return messages;
    }

    /**
     * "Out for delivery" accepted to {@code phone(s)}, then "missed you" accepted to {@code phone(100
     * + s)}: each message keeps the phone it was triggered for (ADR-117).
     */
    private static StopMessages toTwoPhones(int s, Instant ten) {
        StopMessages messages = claimed(s, ten, phone(s));
        messages.accepted(messageId(s, 1), REFERENCE, at(ten, 2));
        messages.onOutcome(
                messageId(s, 2),
                new RecipientPhone.Known(phone(100 + s)),
                MESSAGES_ON,
                new ReportedOutcome.Failed(),
                at(ten, 3));
        messages.claim(messageId(s, 2), ROOM, PhoneConsent.MAY_BE_MESSAGED, at(ten, 4));
        messages.accepted(messageId(s, 2), new ProviderReference("provider-2"), at(ten, 5));
        return messages;
    }

    /** "Out for delivery" for an hour later, triggered at {@code ten} and pending. */
    private static StopMessages pendingOutForDelivery(int s, Instant ten, PhoneNumber phone) {
        StopMessages messages = StopMessages.open(stop(s));
        messages.onRouteStarted(
                messageId(s, 1),
                new RecipientPhone.Known(phone),
                MESSAGES_ON,
                StopProgress.NOT_REACHED,
                carriedForward(at(ten, 60)),
                ten);
        return messages;
    }

    /** "Out for delivery" triggered at {@code ten}, and claimed a minute later. */
    private static StopMessages claimed(int s, Instant ten, PhoneNumber phone) {
        StopMessages messages = pendingOutForDelivery(s, ten, phone);
        messages.claim(messageId(s, 1), ROOM, PhoneConsent.MAY_BE_MESSAGED, at(ten, 1));
        return messages;
    }

    /** "Delivery changed" as message 2, pending, the only message of a stop whose route started. */
    private static StopMessages deliveryChanged(int s, Instant ten, PhoneNumber phone) {
        StopMessages messages = StopMessages.open(stop(s));
        messages.onDeliveryChanged(
                messageId(s, 2),
                new RecipientPhone.Known(phone),
                MESSAGES_ON,
                RouteProgress.IN_PROGRESS,
                StopProgress.NOT_REACHED,
                ten);
        return messages;
    }

    private static ArrivalFacts inProgress(Instant arrival) {
        return new ArrivalFacts(
                RouteProgress.IN_PROGRESS, PreviousStop.HAS_OUTCOME, StopProgress.NOT_REACHED, carriedForward(arrival));
    }

    private static ProjectedArrival carriedForward(Instant arrival) {
        return new ProjectedArrival(arrival, ProjectedArrival.Basis.CARRIED_FORWARD);
    }

    /** Messages on and the phone not opted out, with the rating question as given. */
    private static SendingConditions conditions(Switch ratingQuestion) {
        return new SendingConditions(
                Switch.ON, ratingQuestion, DISPATCH, COMPANY, MessageLanguage.HEBREW, PhoneConsent.MAY_BE_MESSAGED);
    }

    // --- keys, times and stored values

    private static StopId stop(int s) {
        return StopId.of(new UUID(KEY_PREFIX, s));
    }

    private static OutgoingMessageId messageId(int s, int m) {
        return OutgoingMessageId.of(new UUID(KEY_PREFIX + 1, 100L * s + m));
    }

    private static OutgoingMessageId answerId(int n, int a) {
        return OutgoingMessageId.of(new UUID(KEY_PREFIX + 2, 100L * n + a));
    }

    private static FreshIds ids(int s, int... messages) {
        return FreshIds.of(
                Arrays.stream(messages).mapToObj(m -> messageId(s, m).value()).toList());
    }

    /** An Israeli mobile number of its own for each {@code n}. */
    private static PhoneNumber phone(int n) {
        return PhoneNumber.ofStored("+97252%07d".formatted(3_000_000 + n));
    }

    private static LocalDate dayOf(int n) {
        return FIRST_DAY.plusDays((long) DAYS_APART * n);
    }

    /** 10:00 in Israel on test {@code n}'s day. */
    private static Instant tenOn(int n) {
        return dayOf(n).atTime(TEN).atZone(IsraelDateTime.ZONE).toInstant();
    }

    private static Instant at(Instant ten, int minutesLater) {
        return ten.plus(Duration.ofMinutes(minutesLater));
    }

    /**
     * Deletes the expired stops in a session whose zone is UTC, not the JVM's, so the count shows
     * the SQL keeps Israel's calendar whatever the session's zone.
     */
    private int deleteExpiredInAUtcSession(Instant now) {
        jdbc.sql("SET LOCAL TimeZone = 'UTC'").update();
        return repository.deleteExpired(now);
    }

    private void shortLockTimeout() {
        jdbc.sql("SET LOCAL lock_timeout = '200ms'").update();
    }

    /** Stores {@code count} answers counted on a day, keyed apart from {@link #answerId}. */
    private void storeCountedAnswers(int n, LocalDate day, int count) {
        jdbc.sql("""
                        INSERT INTO messaging.counted_answer (answer_id, day)
                        SELECT CAST('0190a5e2-' || lpad(CAST(? AS text), 4, '0') || '-7000-8000-'
                                    || lpad(CAST(g AS text), 12, '0') AS uuid), ?
                        FROM generate_series(1, ?) AS g
                        """).params(n, day, count).update();
    }

    private long storedVersion(int s) {
        return jdbc.sql("SELECT version FROM messaging.stop_messages WHERE id = ?")
                .param(stop(s).value())
                .query(Long.class)
                .single();
    }

    private String storedState(OutgoingMessageId message) {
        return jdbc.sql("SELECT state FROM messaging.outgoing_message WHERE id = ?")
                .param(message.value())
                .query(String.class)
                .single();
    }

    private long storedMessages(int s) {
        return jdbc.sql("SELECT count(*) FROM messaging.outgoing_message WHERE stop_messages_id = ?")
                .param(stop(s).value())
                .query(Long.class)
                .single();
    }
}
