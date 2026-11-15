package com.dispatchflow.messaging.domain.message;

import static com.dispatchflow.messaging.domain.MessagingFixtures.LANDLINE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.OTHER_MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.STOP;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TO_MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TO_OTHER_MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.at;
import static com.dispatchflow.messaging.domain.MessagingFixtures.conditions;
import static com.dispatchflow.messaging.domain.MessagingFixtures.freshIds;
import static com.dispatchflow.messaging.domain.MessagingFixtures.messageId;
import static com.dispatchflow.messaging.domain.MessagingFixtures.on;
import static com.dispatchflow.messaging.domain.MessagingFixtures.planned;
import static com.dispatchflow.messaging.domain.MessagingFixtures.projected;
import static com.dispatchflow.messaging.domain.MessagingFixtures.quarter;
import static com.dispatchflow.messaging.domain.message.StopMessagesBuilder.aStopMessages;
import static com.dispatchflow.messaging.domain.message.StopMessagesBuilder.outForDelivery;
import static com.dispatchflow.messaging.domain.message.StopMessagesBuilder.plain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.messaging.domain.MessagingFixtures;
import com.dispatchflow.messaging.domain.PhoneConsent;
import com.dispatchflow.messaging.domain.QuarterHour;
import com.dispatchflow.messaging.domain.SendingConditions;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.messaging.domain.Wording;
import com.dispatchflow.messaging.domain.message.ArrivalFacts.PreviousStop;
import com.dispatchflow.messaging.domain.message.MessageState.DropCause;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Which messages a stop's triggers add (DL §4.6): FR-348, FR-350 to FR-356, FR-372 to FR-374,
 * FR-378, FR-384 and NFR-191. A trigger that may not add a message adds nothing and refuses
 * nothing.
 */
class StopMessagesTriggersTest {

    private static final ProviderReference REFERENCE = new ProviderReference("provider-1");
    private static final MessageState ACCEPTED = new MessageState.Accepted(TEN, REFERENCE);

    private static StopMessagesSnapshot expected(OutgoingMessage... messages) {
        return new StopMessagesSnapshot(STOP, StopMessagesBuilder.LOADED, List.of(messages));
    }

    private static OutgoingMessage pendingMessage(
            int n,
            MessageType type,
            int sequence,
            String text,
            Optional<QuarterHour> arrival,
            boolean asks,
            Instant triggered) {
        return new OutgoingMessage(
                messageId(n),
                type,
                sequence,
                MOBILE,
                MessageText.ofStored(text),
                arrival,
                asks,
                triggered,
                new MessageState.Pending(triggered));
    }

    private static void assertNothingAdded(StopMessages messages, Consumer<StopMessages> trigger) {
        StopMessagesSnapshot before = messages.snapshot();

        trigger.accept(messages);

        assertThat(messages.snapshot()).isEqualTo(before);
        assertThat(messages.pendingEvents()).isEmpty();
    }

    private static ArrivalFacts facts(RouteProgress route, PreviousStop previous, StopProgress stop, Instant at) {
        return new ArrivalFacts(route, previous, stop, projected(at));
    }

    private static ArrivalFacts inProgress(Instant arrival) {
        return facts(RouteProgress.IN_PROGRESS, PreviousStop.HAS_OUTCOME, StopProgress.NOT_REACHED, arrival);
    }

    /** The first stop of a route in progress, skipped: its planned arrival, which only follows now. */
    private static ArrivalFacts skipped(Instant arrival) {
        return new ArrivalFacts(
                RouteProgress.IN_PROGRESS, PreviousStop.NONE, StopProgress.NOT_REACHED, planned(arrival));
    }

    // --- out for delivery (FR-351, FR-372, FR-373)

    @Test
    void GivenANewStop_WhenItsRouteStarts_ThenOutForDeliveryGivesTheRoundedWindowToTheRecipientsPhone() {
        StopMessages messages = StopMessages.open(STOP);

        messages.onRouteStarted(
                messageId(1), TO_MOBILE, on(), StopProgress.NOT_REACHED, projected(at(67).plusSeconds(30)), TEN);

        assertThat(messages.snapshot())
                .isEqualTo(new StopMessagesSnapshot(
                        STOP,
                        Version.NEW,
                        List.of(pendingMessage(
                                1,
                                MessageType.OUT_FOR_DELIVERY,
                                1,
                                "Shefa Deliveries: המשלוח בדרך ויגיע היום בין 10:45–11:45",
                                Optional.of(quarter(75)),
                                false,
                                TEN))));
        assertThat(messages.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAStopThatJoinsARouteInProgress_WhenItJoins_ThenItGetsOutForDelivery() {
        StopMessages messages = aStopMessages().build();

        messages.onJoinedStartedRoute(
                messageId(1), TO_MOBILE, on(), StopProgress.NOT_REACHED, projected(at(60)), at(5));

        assertThat(messages.snapshot())
                .isEqualTo(expected(pendingMessage(
                        1,
                        MessageType.OUT_FOR_DELIVERY,
                        1,
                        "Shefa Deliveries: המשלוח בדרך ויגיע היום בין 10:30–11:30",
                        Optional.of(quarter(60)),
                        false,
                        at(5))));
    }

    static Stream<Arguments> noOutForDelivery() {
        return Stream.of(
                Arguments.of(
                        "messages off",
                        TO_MOBILE,
                        conditions(Switch.OFF, Switch.OFF, PhoneConsent.MAY_BE_MESSAGED),
                        StopProgress.NOT_REACHED),
                Arguments.of(
                        "opted out",
                        TO_MOBILE,
                        conditions(Switch.ON, Switch.OFF, PhoneConsent.OPTED_OUT),
                        StopProgress.NOT_REACHED),
                Arguments.of("a landline", new RecipientPhone.Known(LANDLINE), on(), StopProgress.NOT_REACHED),
                Arguments.of(
                        "a foreign mobile",
                        new RecipientPhone.Known(PhoneNumber.parse("+1 202 555 0143")),
                        on(),
                        StopProgress.NOT_REACHED),
                Arguments.of("no phone", new RecipientPhone.None(), on(), StopProgress.NOT_REACHED),
                Arguments.of("arrived", TO_MOBILE, on(), StopProgress.ARRIVED),
                Arguments.of("with an outcome", TO_MOBILE, on(), StopProgress.HAS_OUTCOME));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noOutForDelivery")
    void GivenAConditionThatForbidsIt_WhenTheRouteStartsOrTheStopJoins_ThenNoMessageIsAdded(
            String why, RecipientPhone to, SendingConditions conditions, StopProgress progress) {
        StopMessages messages = aStopMessages().build();

        assertNothingAdded(
                messages, m -> m.onRouteStarted(messageId(1), to, conditions, progress, projected(at(60)), TEN));
        assertNothingAdded(
                messages, m -> m.onJoinedStartedRoute(messageId(1), to, conditions, progress, projected(at(60)), TEN));
    }

    @Test
    void GivenOutForDeliveryAlreadyTriggered_WhenTheStopJoinsAnotherStartedRoute_ThenNoSecondOneIsAdded() {
        StopMessages messages = aStopMessages()
                .with(outForDelivery(1, new MessageState.Dropped(TEN, DropCause.STALE)))
                .build();

        assertNothingAdded(
                messages,
                m -> m.onJoinedStartedRoute(
                        messageId(2), TO_MOBILE, on(), StopProgress.NOT_REACHED, projected(at(90)), at(30)));
    }

    // --- arriving soon (FR-352)

    @Test
    void GivenARouteInProgressAndTheStopBeforeDone_WhenTheArrivalIsExactly15MinutesAway_ThenArrivingSoonIsAdded() {
        StopMessages messages = aStopMessages().build();

        messages.onArrivalProjected(freshIds(1), TO_MOBILE, on(), inProgress(at(15)), TEN);

        assertThat(messages.snapshot())
                .isEqualTo(expected(pendingMessage(
                        1,
                        MessageType.ARRIVING_SOON,
                        1,
                        "Shefa Deliveries: הנהג יגיע אליך בעוד כ-15 דקות",
                        Optional.empty(),
                        false,
                        TEN)));
    }

    @Test
    void GivenTheFirstStopOfARouteInProgress_WhenItIs10MinutesAway_ThenArrivingSoonIsAdded() {
        StopMessages messages = aStopMessages().build();

        messages.onArrivalProjected(
                freshIds(1),
                TO_MOBILE,
                on(),
                facts(RouteProgress.IN_PROGRESS, PreviousStop.NONE, StopProgress.NOT_REACHED, at(10)),
                TEN);

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::type)
                .containsExactly(MessageType.ARRIVING_SOON);
    }

    /**
     * The guards both projection messages share (FR-350, FR-363, FR-378), each with an arrival sent
     * for 11:00 and a projection 15 minutes away at 10:15, which would otherwise add both messages.
     */
    static Stream<Arguments> guards() {
        StopMessagesBuilder arrivalSent = aStopMessages().with(outForDelivery(1, ACCEPTED));
        ArrivalFacts both = inProgress(at(15));
        return Stream.of(
                Arguments.of(
                        "messages off",
                        arrivalSent,
                        TO_MOBILE,
                        conditions(Switch.OFF, Switch.OFF, PhoneConsent.MAY_BE_MESSAGED),
                        both),
                Arguments.of(
                        "opted out",
                        arrivalSent,
                        TO_MOBILE,
                        conditions(Switch.ON, Switch.OFF, PhoneConsent.OPTED_OUT),
                        both),
                Arguments.of("a landline", arrivalSent, new RecipientPhone.Known(LANDLINE), on(), both),
                Arguments.of("no phone", arrivalSent, new RecipientPhone.None(), on(), both));
    }

    static Stream<Arguments> noArrivingSoon() {
        Instant soon = at(10);
        return Stream.concat(
                Stream.of(
                        Arguments.of(
                                "15 minutes and a second away",
                                aStopMessages(),
                                TO_MOBILE,
                                on(),
                                inProgress(at(15).plusSeconds(1))),
                        Arguments.of(
                                "a skipped stop's planned arrival (ADR-114)",
                                aStopMessages(),
                                TO_MOBILE,
                                on(),
                                skipped(soon)),
                        Arguments.of(
                                "route not started",
                                aStopMessages(),
                                TO_MOBILE,
                                on(),
                                facts(
                                        RouteProgress.NOT_STARTED,
                                        PreviousStop.HAS_OUTCOME,
                                        StopProgress.NOT_REACHED,
                                        soon)),
                        Arguments.of(
                                "route finished",
                                aStopMessages(),
                                TO_MOBILE,
                                on(),
                                facts(
                                        RouteProgress.FINISHED,
                                        PreviousStop.HAS_OUTCOME,
                                        StopProgress.NOT_REACHED,
                                        soon)),
                        Arguments.of(
                                "stop before has no outcome",
                                aStopMessages(),
                                TO_MOBILE,
                                on(),
                                facts(
                                        RouteProgress.IN_PROGRESS,
                                        PreviousStop.NO_OUTCOME,
                                        StopProgress.NOT_REACHED,
                                        soon)),
                        Arguments.of(
                                "stop arrived",
                                aStopMessages(),
                                TO_MOBILE,
                                on(),
                                facts(RouteProgress.IN_PROGRESS, PreviousStop.HAS_OUTCOME, StopProgress.ARRIVED, soon)),
                        Arguments.of(
                                "stop has an outcome",
                                aStopMessages(),
                                TO_MOBILE,
                                on(),
                                facts(
                                        RouteProgress.IN_PROGRESS,
                                        PreviousStop.HAS_OUTCOME,
                                        StopProgress.HAS_OUTCOME,
                                        soon))),
                guards());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noArrivingSoon")
    void GivenAConditionThatForbidsIt_WhenTheArrivalIsProjected_ThenArrivingSoonIsNotAdded(
            String why,
            StopMessagesBuilder state,
            RecipientPhone to,
            SendingConditions conditions,
            ArrivalFacts facts) {
        assertNothingAdded(state.build(), m -> m.onArrivalProjected(freshIds(2, 3), to, conditions, facts, TEN));
    }

    @Test
    void GivenArrivingSoonAlreadyAdded_WhenTheArrivalIsProjectedAgain_ThenNoSecondOneIsAdded() {
        StopMessages messages = aStopMessages()
                .with(plain(1, MessageType.ARRIVING_SOON, StopMessagesBuilder.pendingSinceTen()))
                .build();

        assertNothingAdded(
                messages, m -> m.onArrivalProjected(freshIds(2), TO_MOBILE, on(), inProgress(at(10)), at(1)));
    }

    // --- a skipped stop's planned arrival (ADR-114)

    @Test
    void
            GivenASkippedStopWhosePlannedArrivalHasPassed_WhenTheMinuteJobProjectsItEachHalfHour_ThenOnlyOutForDeliveryIsAdded() {
        StopMessages messages = StopMessages.open(STOP);
        messages.onRouteStarted(messageId(1), TO_MOBILE, on(), StopProgress.NOT_REACHED, planned(TEN), TEN);

        messages.onArrivalProjected(freshIds(2, 3), TO_MOBILE, on(), skipped(at(1)), at(1));
        messages.onArrivalProjected(freshIds(4, 5), TO_MOBILE, on(), skipped(at(31)), at(31));
        messages.onArrivalProjected(freshIds(6, 7), TO_MOBILE, on(), skipped(at(61)), at(61));
        messages.onArrivalProjected(freshIds(8, 9), TO_MOBILE, on(), skipped(at(91)), at(91));

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::id, OutgoingMessage::type, OutgoingMessage::arrivalSent)
                .containsExactly(tuple(messageId(1), MessageType.OUT_FOR_DELIVERY, Optional.of(quarter(0))));
    }

    @Test
    void GivenASkippedStop_WhenAnArrivalCarriedForwardReachesIt_ThenArrivingSoonAndTimeChangedAreAddedAgain() {
        StopMessages messages = StopMessages.open(STOP);
        messages.onRouteStarted(messageId(1), TO_MOBILE, on(), StopProgress.NOT_REACHED, planned(TEN), TEN);
        messages.onArrivalProjected(freshIds(2, 3), TO_MOBILE, on(), skipped(at(31)), at(31));

        messages.onArrivalProjected(freshIds(4, 5), TO_MOBILE, on(), inProgress(at(40)), at(31));

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::id, OutgoingMessage::type)
                .containsExactly(
                        tuple(messageId(1), MessageType.OUT_FOR_DELIVERY),
                        tuple(messageId(4), MessageType.ARRIVING_SOON),
                        tuple(messageId(5), MessageType.TIME_CHANGED));
    }

    // --- time changed (FR-353, FR-384)

    @Test
    void GivenAnArrivalSentFor1100_WhenTheProjectionIs29Minutes59SecondsLater_ThenNoTimeChangedIsAdded() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        assertNothingAdded(
                messages,
                m -> m.onArrivalProjected(freshIds(2), TO_MOBILE, on(), inProgress(at(89).plusSeconds(59)), TEN));
    }

    @Test
    void GivenAnArrivalSentFor1100_WhenTheProjectionIs30MinutesLater_ThenTimeChangedGivesTheNewWindow() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        messages.onArrivalProjected(freshIds(2), TO_MOBILE, on(), inProgress(at(90)), TEN);

        assertThat(messages.snapshot())
                .isEqualTo(expected(
                        outForDelivery(1, ACCEPTED),
                        pendingMessage(
                                2,
                                MessageType.TIME_CHANGED,
                                1,
                                "Shefa Deliveries: זמן ההגעה עודכן: היום בין 11:00–12:00",
                                Optional.of(quarter(90)),
                                false,
                                TEN)));
    }

    @Test
    void GivenAnArrivalSentFor1100_WhenTheProjectionIs29Minutes59SecondsEarlier_ThenNoTimeChangedIsAdded() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        assertNothingAdded(
                messages,
                m -> m.onArrivalProjected(freshIds(2), TO_MOBILE, on(), inProgress(at(30).plusSeconds(1)), TEN));
    }

    @Test
    void GivenAnArrivalSentFor1100_WhenTheProjectionIs30MinutesEarlier_ThenTimeChangedIsAdded() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        messages.onArrivalProjected(freshIds(2), TO_MOBILE, on(), inProgress(at(30)), TEN);

        assertThat(messages.snapshot().messages().get(1).arrivalSent()).contains(quarter(30));
    }

    @Test
    void GivenTwoTimeChangesAlready_WhenTheProjectionMovesAgain_ThenNoThirdIsAdded() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        messages.onArrivalProjected(freshIds(2), TO_MOBILE, on(), inProgress(at(90)), TEN);
        messages.onArrivalProjected(freshIds(3), TO_MOBILE, on(), inProgress(at(120)), at(1));
        assertNothingAdded(
                messages, m -> m.onArrivalProjected(freshIds(4), TO_MOBILE, on(), inProgress(at(150)), at(2)));

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::type, OutgoingMessage::sequence, OutgoingMessage::arrivalSent)
                .containsExactly(
                        tuple(MessageType.OUT_FOR_DELIVERY, 1, Optional.of(quarter(60))),
                        tuple(MessageType.TIME_CHANGED, 1, Optional.of(quarter(90))),
                        tuple(MessageType.TIME_CHANGED, 2, Optional.of(quarter(120))));
    }

    @Test
    void GivenAFinishedRouteWithTheStopNotReached_WhenTheProjectionMoves30Minutes_ThenTimeChangedIsAdded() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        messages.onArrivalProjected(
                freshIds(2),
                TO_MOBILE,
                on(),
                facts(RouteProgress.FINISHED, PreviousStop.HAS_OUTCOME, StopProgress.NOT_REACHED, at(90)),
                TEN);

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::type)
                .contains(MessageType.TIME_CHANGED);
    }

    static Stream<Arguments> noTimeChanged() {
        Instant moved = at(100);
        return Stream.concat(
                Stream.of(
                        Arguments.of(
                                "route not started",
                                aStopMessages().with(outForDelivery(1, ACCEPTED)),
                                TO_MOBILE,
                                on(),
                                facts(
                                        RouteProgress.NOT_STARTED,
                                        PreviousStop.HAS_OUTCOME,
                                        StopProgress.NOT_REACHED,
                                        moved)),
                        Arguments.of(
                                "stop arrived",
                                aStopMessages().with(outForDelivery(1, ACCEPTED)),
                                TO_MOBILE,
                                on(),
                                facts(
                                        RouteProgress.IN_PROGRESS,
                                        PreviousStop.HAS_OUTCOME,
                                        StopProgress.ARRIVED,
                                        moved)),
                        Arguments.of(
                                "a skipped stop's planned arrival (ADR-114)",
                                aStopMessages().with(outForDelivery(1, ACCEPTED)),
                                TO_MOBILE,
                                on(),
                                skipped(moved)),
                        Arguments.of("no arrival ever sent", aStopMessages(), TO_MOBILE, on(), inProgress(moved)),
                        Arguments.of(
                                "the only arrival was dropped",
                                aStopMessages().with(outForDelivery(1, new MessageState.Dropped(TEN, DropCause.STALE))),
                                TO_MOBILE,
                                on(),
                                inProgress(moved))),
                guards());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noTimeChanged")
    void GivenAConditionThatForbidsIt_WhenTheProjectionMoves_ThenTimeChangedIsNotAdded(
            String why,
            StopMessagesBuilder state,
            RecipientPhone to,
            SendingConditions conditions,
            ArrivalFacts facts) {
        assertNothingAdded(state.build(), m -> m.onArrivalProjected(freshIds(2, 3), to, conditions, facts, TEN));
    }

    // --- the arrival last sent (FR-353, ADR-108)

    @Test
    void GivenATimeChangedSentFor1130_WhenTheProjectionIs1145_ThenNoSecondTimeChangedIsAdded() {
        StopMessages messages = aStopMessages()
                .with(outForDelivery(1, ACCEPTED))
                .with(timeChanged(2, 1, quarter(90), ACCEPTED))
                .build();

        assertNothingAdded(messages, m -> m.onArrivalProjected(freshIds(3), TO_MOBILE, on(), inProgress(at(105)), TEN));
    }

    @Test
    void GivenTheLatestTimeChangedDropped_WhenTheProjectionIs30MinutesFromTheOutForDelivery_ThenTimeChangedIsAdded() {
        StopMessages messages = aStopMessages()
                .with(outForDelivery(1, ACCEPTED))
                .with(timeChanged(2, 1, quarter(90), new MessageState.Dropped(TEN, DropCause.STALE)))
                .build();

        messages.onArrivalProjected(freshIds(3), TO_MOBILE, on(), inProgress(at(90)), TEN);

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::id, OutgoingMessage::type, OutgoingMessage::sequence)
                .containsExactly(
                        tuple(messageId(1), MessageType.OUT_FOR_DELIVERY, 1),
                        tuple(messageId(2), MessageType.TIME_CHANGED, 1),
                        tuple(messageId(3), MessageType.TIME_CHANGED, 2));
    }

    private static OutgoingMessage timeChanged(int n, int sequence, QuarterHour arrival, MessageState state) {
        return new OutgoingMessage(
                messageId(n),
                MessageType.TIME_CHANGED,
                sequence,
                MOBILE,
                MessagingFixtures.text(new Wording.TimeChanged(arrival)),
                Optional.of(arrival),
                false,
                TEN,
                state);
    }

    @Test
    void GivenAnArrivalSentFor1100_WhenTheStopIs15MinutesAwayAnd45MinutesEarly_ThenBothMessagesAreAdded() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        messages.onArrivalProjected(freshIds(2, 3), TO_MOBILE, on(), inProgress(at(15)), TEN);

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::id, OutgoingMessage::type)
                .containsExactly(
                        tuple(messageId(1), MessageType.OUT_FOR_DELIVERY),
                        tuple(messageId(2), MessageType.ARRIVING_SOON),
                        tuple(messageId(3), MessageType.TIME_CHANGED));
    }

    // --- your delivery changed (FR-354)

    @Test
    void GivenAStartedRoute_WhenTheOrderIsDroppedMovedOrCancelled_ThenYourDeliveryChangedGivesTheDispatchPhone() {
        StopMessages messages = aStopMessages().build();

        messages.onDeliveryChanged(
                messageId(1), TO_MOBILE, on(), RouteProgress.IN_PROGRESS, StopProgress.NOT_REACHED, TEN);

        assertThat(messages.snapshot())
                .isEqualTo(expected(pendingMessage(
                        1,
                        MessageType.DELIVERY_CHANGED,
                        1,
                        "Shefa Deliveries: חל שינוי במשלוח. לפרטים: 035551234",
                        Optional.empty(),
                        false,
                        TEN)));
    }

    @Test
    void GivenARouteNotStartedOrAStopReached_WhenTheOrderChanges_ThenNoMessageIsAdded() {
        assertNothingAdded(
                aStopMessages().build(),
                m -> m.onDeliveryChanged(
                        messageId(1), TO_MOBILE, on(), RouteProgress.NOT_STARTED, StopProgress.NOT_REACHED, TEN));
        assertNothingAdded(
                aStopMessages().build(),
                m -> m.onDeliveryChanged(
                        messageId(1), TO_MOBILE, on(), RouteProgress.IN_PROGRESS, StopProgress.ARRIVED, TEN));
        assertNothingAdded(
                aStopMessages()
                        .with(plain(1, MessageType.DELIVERY_CHANGED, ACCEPTED))
                        .build(),
                m -> m.onDeliveryChanged(
                        messageId(2), TO_MOBILE, on(), RouteProgress.FINISHED, StopProgress.NOT_REACHED, TEN));
    }

    // --- delivered and missed you (FR-355, FR-356, FR-374)

    @Test
    void GivenTheRatingQuestionOff_WhenTheFirstOutcomeIsDeliveredAtTheDoor_ThenDeliveredSaysSoAndAsksNothing() {
        StopMessages messages = aStopMessages().build();

        messages.onOutcome(messageId(1), TO_MOBILE, on(), new ReportedOutcome.Delivered(true), TEN);

        assertThat(messages.snapshot())
                .isEqualTo(expected(pendingMessage(
                        1,
                        MessageType.DELIVERED,
                        1,
                        "Shefa Deliveries: המשלוח נמסר (ליד הדלת).",
                        Optional.empty(),
                        false,
                        TEN)));
    }

    @Test
    void GivenTheRatingQuestionOn_WhenTheFirstOutcomeIsDelivered_ThenDeliveredAsksForARating() {
        StopMessages messages = aStopMessages().build();

        messages.onOutcome(
                messageId(1),
                TO_MOBILE,
                conditions(Switch.ON, Switch.ON, PhoneConsent.MAY_BE_MESSAGED),
                new ReportedOutcome.Delivered(false),
                TEN);

        assertThat(messages.snapshot())
                .isEqualTo(expected(pendingMessage(
                        1,
                        MessageType.DELIVERED,
                        1,
                        "Shefa Deliveries: המשלוח נמסר. איך היה? השב/י במספר 1–10",
                        Optional.empty(),
                        true,
                        TEN)));
    }

    @Test
    void GivenTheFirstOutcomeIsFailed_WhenItIsRecorded_ThenMissedYouGivesTheDispatchPhone() {
        StopMessages messages = aStopMessages().build();

        messages.onOutcome(messageId(1), TO_MOBILE, on(), new ReportedOutcome.Failed(), TEN);

        assertThat(messages.snapshot())
                .isEqualTo(expected(pendingMessage(
                        1,
                        MessageType.MISSED_YOU,
                        1,
                        "Shefa Deliveries: לא הצלחנו למסור את המשלוח. לפרטים: 035551234",
                        Optional.empty(),
                        false,
                        TEN)));
    }

    @Test
    void GivenAnOutcomeAlreadyMessaged_WhenAnotherOutcomeArrives_ThenNoSecondOutcomeMessageIsAdded() {
        StopMessages missed =
                aStopMessages().with(plain(1, MessageType.MISSED_YOU, ACCEPTED)).build();
        StopMessages delivered =
                aStopMessages().with(plain(1, MessageType.DELIVERED, ACCEPTED)).build();

        assertNothingAdded(
                missed, m -> m.onOutcome(messageId(2), TO_MOBILE, on(), new ReportedOutcome.Delivered(false), TEN));
        assertNothingAdded(
                delivered, m -> m.onOutcome(messageId(2), TO_MOBILE, on(), new ReportedOutcome.Failed(), TEN));
        assertNothingAdded(
                delivered, m -> m.onOutcome(messageId(2), TO_MOBILE, on(), new ReportedOutcome.Delivered(false), TEN));
    }

    @Test
    void GivenMessagesOffOrAnOptedOutPhone_WhenTheFirstOutcomeArrives_ThenNoMessageIsAdded() {
        assertNothingAdded(
                aStopMessages().build(),
                m -> m.onOutcome(
                        messageId(1),
                        TO_MOBILE,
                        conditions(Switch.OFF, Switch.ON, PhoneConsent.MAY_BE_MESSAGED),
                        new ReportedOutcome.Failed(),
                        TEN));
        assertNothingAdded(
                aStopMessages().build(),
                m -> m.onOutcome(
                        messageId(1),
                        TO_MOBILE,
                        conditions(Switch.ON, Switch.ON, PhoneConsent.OPTED_OUT),
                        new ReportedOutcome.Failed(),
                        TEN));
    }

    // --- the phone each message goes to (FR-348, ADR-117), and the IDs

    @Test
    void GivenAPendingMessageToOnePhone_WhenALaterTriggerGivesAnother_ThenThePendingMessageStillGoesToItsOwnPhone() {
        StopMessages messages = StopMessages.open(STOP);

        messages.onRouteStarted(messageId(1), TO_MOBILE, on(), StopProgress.NOT_REACHED, projected(at(60)), TEN);
        messages.onDeliveryChanged(
                messageId(2), TO_OTHER_MOBILE, on(), RouteProgress.IN_PROGRESS, StopProgress.NOT_REACHED, at(1));

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::id, OutgoingMessage::phone, OutgoingMessage::state)
                .containsExactly(
                        tuple(messageId(1), MOBILE, new MessageState.Pending(TEN)),
                        tuple(messageId(2), OTHER_MOBILE, new MessageState.Pending(at(1))));
    }

    @Test
    void GivenAMessageAcceptedForOnePhone_WhenALaterTriggerGivesAnother_ThenTheAcceptedMessageStillShowsItsOwnPhone() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        messages.onOutcome(messageId(2), TO_OTHER_MOBILE, on(), new ReportedOutcome.Failed(), at(30));

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::id, OutgoingMessage::phone)
                .containsExactly(tuple(messageId(1), MOBILE), tuple(messageId(2), OTHER_MOBILE));
    }

    @Test
    void GivenAProjectionAddingTwoMessages_WhenItNamesAnotherPhoneThanTheArrivalSent_ThenBothGoToTheNamedPhone() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        messages.onArrivalProjected(freshIds(2, 3), TO_OTHER_MOBILE, on(), inProgress(at(15)), TEN);

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::type, OutgoingMessage::phone)
                .containsExactly(
                        tuple(MessageType.OUT_FOR_DELIVERY, MOBILE),
                        tuple(MessageType.ARRIVING_SOON, OTHER_MOBILE),
                        tuple(MessageType.TIME_CHANGED, OTHER_MOBILE));
    }

    @Test
    void GivenAMessageWithAnId_WhenATriggerReusesTheId_ThenItFailsAsABug() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        assertThatThrownBy(() -> messages.onOutcome(messageId(1), TO_MOBILE, on(), new ReportedOutcome.Failed(), TEN))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void GivenAPendingMessage_WhenMessagesAreTurnedOffAfterItsTrigger_ThenItIsStillClaimed() {
        StopMessages messages = aStopMessages()
                .with(outForDelivery(1, new MessageState.Pending(TEN)))
                .build();
        SendingConditions off = conditions(Switch.OFF, Switch.OFF, PhoneConsent.MAY_BE_MESSAGED);

        messages.onOutcome(messageId(2), TO_MOBILE, off, new ReportedOutcome.Failed(), at(1));

        assertThat(messages.claim(messageId(1), new DailySendCap(0), PhoneConsent.MAY_BE_MESSAGED, at(2)))
                .isEqualTo(StopMessages.Claim.CLAIMED);
        assertThat(messages.snapshot().messages()).hasSize(1);
    }
}
