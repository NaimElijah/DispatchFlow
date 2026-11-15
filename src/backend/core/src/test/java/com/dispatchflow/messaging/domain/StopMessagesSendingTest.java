package com.dispatchflow.messaging.domain;

import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.OTHER_MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.STOP;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.at;
import static com.dispatchflow.messaging.domain.MessagingFixtures.messageId;
import static com.dispatchflow.messaging.domain.MessagingFixtures.text;
import static com.dispatchflow.messaging.domain.StopMessagesBuilder.aStopMessages;
import static com.dispatchflow.messaging.domain.StopMessagesBuilder.outForDelivery;
import static com.dispatchflow.messaging.domain.StopMessagesBuilder.plain;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.messaging.domain.MessageState.DropCause;
import com.dispatchflow.messaging.domain.MessagingError.MessageNotClaimable;
import com.dispatchflow.messaging.domain.MessagingError.MessageNotSending;
import com.dispatchflow.messaging.domain.MessagingError.UnknownMessage;
import com.dispatchflow.messaging.domain.StopMessages.Claim;
import com.dispatchflow.messaging.domain.StopMessages.ProviderRepeats;
import com.dispatchflow.shared.DomainError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A message's lifecycle under the sender (DL §9.5): claim, the provider's answer, unknown outcomes
 * (NFR-198), the 15-minute drop (NFR-190) and the daily cap (NFR-196).
 */
class StopMessagesSendingTest {

    private static final ProviderReference REFERENCE = new ProviderReference("provider-1");
    private static final DailySendCap ROOM = new DailySendCap(0);
    private static final Instant SOON = at(5);
    private static final MessageState ACCEPTED_TODAY = new MessageState.Accepted(at(1), REFERENCE);

    private static void assertRefusedUnchanged(StopMessages messages, ThrowingCallable call, DomainError expected) {
        StopMessagesSnapshot before = messages.snapshot();

        assertRefused(call, expected);

        assertThat(messages.snapshot()).isEqualTo(before);
        assertThat(messages.pendingEvents()).isEmpty();
    }

    private static MessageState stateOf(StopMessages messages) {
        return messages.snapshot().messages().get(0).state();
    }

    // --- the lifecycle: every state against every command

    enum State {
        PENDING,
        SENDING,
        ACCEPTED,
        UNKNOWN,
        DROPPED,
        DROPPED_STALE_WHILE_SENDING
    }

    interface Command {
        void run(StopMessages messages);
    }

    private static MessageState stateFor(State state) {
        return switch (state) {
            case PENDING -> new MessageState.Pending(TEN);
            case SENDING -> new MessageState.Sending(at(1));
            case ACCEPTED -> new MessageState.Accepted(at(1), REFERENCE);
            case UNKNOWN -> new MessageState.Unknown(at(1));
            case DROPPED -> new MessageState.Dropped(at(1), DropCause.REFUSED);
            case DROPPED_STALE_WHILE_SENDING -> new MessageState.Dropped(at(1), DropCause.STALE_WHILE_SENDING);
        };
    }

    private static StopMessages inState(State state) {
        return aStopMessages().with(outForDelivery(1, stateFor(state))).build();
    }

    static Stream<Arguments> lifecycle() {
        Command claim = messages -> messages.claim(messageId(1), ROOM, PhoneConsent.MAY_BE_MESSAGED, SOON);
        Command accept = messages -> messages.accepted(messageId(1), REFERENCE, SOON);
        Command refuse = messages -> messages.refused(messageId(1), SOON);
        Command retryable =
                messages -> messages.outcomeUnknown(messageId(1), ProviderRepeats.DROPPED_BY_REFERENCE, SOON);
        Command notRetryable = messages -> messages.outcomeUnknown(messageId(1), ProviderRepeats.NOT_DROPPED, SOON);
        Command dropStale = messages -> messages.dropStale(at(16));
        DomainError notClaimable = new MessageNotClaimable(messageId(1));
        DomainError notSending = new MessageNotSending(messageId(1));
        MessageState sending = new MessageState.Sending(SOON);
        MessageState accepted = new MessageState.Accepted(SOON, REFERENCE);
        MessageState refused = new MessageState.Dropped(SOON, DropCause.REFUSED);
        MessageState unknown = new MessageState.Unknown(SOON);
        MessageState lost = new MessageState.Dropped(SOON, DropCause.OUTCOME_UNKNOWN);
        MessageState stale = new MessageState.Dropped(at(16), DropCause.STALE);
        MessageState staleWhileSending = new MessageState.Dropped(at(16), DropCause.STALE_WHILE_SENDING);
        MessageState staleUnknown = new MessageState.Dropped(at(16), DropCause.OUTCOME_UNKNOWN);
        return Stream.of(
                Arguments.of(State.PENDING, "claim", claim, sending, null),
                Arguments.of(State.PENDING, "accepted", accept, null, notSending),
                Arguments.of(State.PENDING, "refused", refuse, null, notSending),
                Arguments.of(State.PENDING, "unknown, retryable", retryable, null, notSending),
                Arguments.of(State.PENDING, "unknown, not retryable", notRetryable, null, notSending),
                Arguments.of(State.PENDING, "drop stale", dropStale, stale, null),
                Arguments.of(State.SENDING, "claim", claim, null, notClaimable),
                Arguments.of(State.SENDING, "accepted", accept, accepted, null),
                Arguments.of(State.SENDING, "refused", refuse, refused, null),
                Arguments.of(State.SENDING, "unknown, retryable", retryable, unknown, null),
                Arguments.of(State.SENDING, "unknown, not retryable", notRetryable, lost, null),
                Arguments.of(State.SENDING, "drop stale", dropStale, staleWhileSending, null),
                Arguments.of(State.ACCEPTED, "claim", claim, null, notClaimable),
                Arguments.of(State.ACCEPTED, "accepted", accept, null, notSending),
                Arguments.of(State.ACCEPTED, "refused", refuse, null, notSending),
                Arguments.of(State.ACCEPTED, "unknown, retryable", retryable, null, notSending),
                Arguments.of(State.ACCEPTED, "unknown, not retryable", notRetryable, null, notSending),
                Arguments.of(
                        State.ACCEPTED, "drop stale", dropStale, new MessageState.Accepted(at(1), REFERENCE), null),
                Arguments.of(State.UNKNOWN, "claim", claim, sending, null),
                Arguments.of(State.UNKNOWN, "accepted", accept, null, notSending),
                Arguments.of(State.UNKNOWN, "refused", refuse, null, notSending),
                Arguments.of(State.UNKNOWN, "unknown, retryable", retryable, null, notSending),
                Arguments.of(State.UNKNOWN, "unknown, not retryable", notRetryable, null, notSending),
                Arguments.of(State.UNKNOWN, "drop stale", dropStale, staleUnknown, null),
                Arguments.of(State.DROPPED, "claim", claim, null, notClaimable),
                Arguments.of(State.DROPPED, "accepted", accept, null, notSending),
                Arguments.of(State.DROPPED, "refused", refuse, null, notSending),
                Arguments.of(State.DROPPED, "unknown, retryable", retryable, null, notSending),
                Arguments.of(State.DROPPED, "unknown, not retryable", notRetryable, null, notSending),
                Arguments.of(
                        State.DROPPED,
                        "drop stale",
                        dropStale,
                        new MessageState.Dropped(at(1), DropCause.REFUSED),
                        null),
                Arguments.of(State.DROPPED_STALE_WHILE_SENDING, "claim", claim, null, notClaimable),
                Arguments.of(State.DROPPED_STALE_WHILE_SENDING, "accepted", accept, accepted, null),
                Arguments.of(State.DROPPED_STALE_WHILE_SENDING, "refused", refuse, null, notSending),
                Arguments.of(State.DROPPED_STALE_WHILE_SENDING, "unknown, retryable", retryable, null, notSending),
                Arguments.of(
                        State.DROPPED_STALE_WHILE_SENDING, "unknown, not retryable", notRetryable, null, notSending),
                Arguments.of(
                        State.DROPPED_STALE_WHILE_SENDING,
                        "drop stale",
                        dropStale,
                        new MessageState.Dropped(at(1), DropCause.STALE_WHILE_SENDING),
                        null));
    }

    @ParameterizedTest(name = "{0} message, {1}")
    @MethodSource("lifecycle")
    void GivenAMessageInEachState_WhenEachSenderCommandRuns_ThenItReachesTheAllowedStateOrRefusesWithTheNamedError(
            State state, String name, Command command, MessageState reached, DomainError error) {
        StopMessages messages = inState(state);

        if (error != null) {
            assertRefusedUnchanged(messages, () -> command.run(messages), error);
        } else {
            command.run(messages);
            assertThat(stateOf(messages)).isEqualTo(reached);
            assertThat(messages.pendingEvents()).isEmpty();
        }
    }

    // --- refusals, on every command that has them

    static Stream<Arguments> everySenderCommand() {
        return Stream.of(
                Arguments.of("claim", (Command) m -> m.claim(messageId(9), ROOM, PhoneConsent.MAY_BE_MESSAGED, SOON)),
                Arguments.of("accepted", (Command) m -> m.accepted(messageId(9), REFERENCE, SOON)),
                Arguments.of("refused", (Command) m -> m.refused(messageId(9), SOON)),
                Arguments.of("outcome unknown", (Command)
                        m -> m.outcomeUnknown(messageId(9), ProviderRepeats.DROPPED_BY_REFERENCE, SOON)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everySenderCommand")
    void GivenAStopWithoutTheMessage_WhenTheSenderNamesIt_ThenUnknownMessage(String name, Command command) {
        StopMessages messages = inState(State.PENDING);

        assertRefusedUnchanged(messages, () -> command.run(messages), new UnknownMessage(messageId(9)));
    }

    @ParameterizedTest
    @MethodSource("notClaimableStates")
    void GivenAMessageNeitherPendingNorUnknown_WhenClaimed_ThenMessageNotClaimable(State state) {
        StopMessages messages = inState(state);

        assertRefusedUnchanged(
                messages,
                () -> messages.claim(messageId(1), ROOM, PhoneConsent.MAY_BE_MESSAGED, SOON),
                new MessageNotClaimable(messageId(1)));
    }

    static Stream<State> notClaimableStates() {
        return Stream.of(State.SENDING, State.ACCEPTED, State.DROPPED);
    }

    static Stream<Arguments> answersForAMessageNotSending() {
        return Stream.of(State.PENDING, State.ACCEPTED, State.UNKNOWN, State.DROPPED)
                .flatMap(state -> Stream.of(
                        Arguments.of(state, "accepted", (Command) m -> m.accepted(messageId(1), REFERENCE, SOON)),
                        Arguments.of(state, "refused", (Command) m -> m.refused(messageId(1), SOON)),
                        Arguments.of(state, "outcome unknown", (Command)
                                m -> m.outcomeUnknown(messageId(1), ProviderRepeats.NOT_DROPPED, SOON))));
    }

    @ParameterizedTest(name = "{0} message, {1}")
    @MethodSource("answersForAMessageNotSending")
    void GivenAMessageNotSending_WhenAProviderAnswerIsReported_ThenMessageNotSending(
            State state, String name, Command command) {
        StopMessages messages = inState(state);

        assertRefusedUnchanged(messages, () -> command.run(messages), new MessageNotSending(messageId(1)));
    }

    // --- claims (NFR-190, NFR-196, FR-363)

    @Test
    void GivenAMessageExactly15MinutesPastItsTrigger_WhenClaimed_ThenItIsSending() {
        StopMessages messages = inState(State.PENDING);

        assertThat(messages.claim(messageId(1), ROOM, PhoneConsent.MAY_BE_MESSAGED, at(15)))
                .isEqualTo(Claim.CLAIMED);
        assertThat(stateOf(messages)).isEqualTo(new MessageState.Sending(at(15)));
    }

    @Test
    void GivenAMessageASecondPast15Minutes_WhenClaimed_ThenItIsDroppedAsStale() {
        StopMessages messages = inState(State.PENDING);
        Instant late = at(15).plusSeconds(1);

        assertThat(messages.claim(messageId(1), ROOM, PhoneConsent.MAY_BE_MESSAGED, late))
                .isEqualTo(Claim.DROPPED);
        assertThat(stateOf(messages)).isEqualTo(new MessageState.Dropped(late, DropCause.STALE));
    }

    @Test
    void GivenAPhoneThatOptedOutAfterTheTrigger_WhenClaimed_ThenTheMessageIsDropped() {
        StopMessages messages = inState(State.PENDING);

        assertThat(messages.claim(messageId(1), ROOM, PhoneConsent.OPTED_OUT, SOON))
                .isEqualTo(Claim.DROPPED);
        assertThat(stateOf(messages)).isEqualTo(new MessageState.Dropped(SOON, DropCause.OPTED_OUT));
    }

    @Test
    void
            GivenAMessageOfUnknownOutcome_WhenClaimedAfterItsPhoneOptedOut_ThenItIsDroppedWithItsOutcomeUnknownAndStillCounts() {
        StopMessages messages = inState(State.UNKNOWN);

        assertThat(messages.claim(messageId(1), ROOM, PhoneConsent.OPTED_OUT, SOON))
                .isEqualTo(Claim.DROPPED);

        MessageState dropped = stateOf(messages);
        assertThat(dropped).isEqualTo(new MessageState.Dropped(SOON, DropCause.OUTCOME_UNKNOWN));
        List<MessageState> claimedToday = new ArrayList<>(Collections.nCopies(999, ACCEPTED_TODAY));
        claimedToday.add(dropped);
        assertThat(DailySendCap.counting(claimedToday).hasRoom()).isFalse();
    }

    @Test
    void GivenTheDaysCapReached_WhenClaimed_ThenTheMessageWaitsUnchanged() {
        StopMessages messages = inState(State.PENDING);
        StopMessagesSnapshot before = messages.snapshot();

        assertThat(messages.claim(messageId(1), new DailySendCap(1_000), PhoneConsent.MAY_BE_MESSAGED, SOON))
                .isEqualTo(Claim.WAITING);
        assertThat(messages.snapshot()).isEqualTo(before);
        assertThat(messages.claim(messageId(1), new DailySendCap(999), PhoneConsent.MAY_BE_MESSAGED, SOON))
                .isEqualTo(Claim.CLAIMED);
    }

    // --- dropping stale messages (NFR-190)

    @Test
    void GivenMessagesOfEveryState_WhenStaleOnesAreDropped_ThenEveryUnendedOnePast15MinutesIsDroppedAndCounted() {
        StopMessages messages = aStopMessages()
                .with(outForDelivery(1, new MessageState.Sending(at(1))))
                .with(plain(2, MessageType.ARRIVING_SOON, new MessageState.Unknown(at(1))))
                .with(plain(3, MessageType.DELIVERY_CHANGED, new MessageState.Pending(TEN)))
                .with(plain(4, MessageType.DELIVERED, new MessageState.Accepted(at(1), REFERENCE)))
                .build();
        Instant late = at(15).plusSeconds(1);

        assertThat(messages.dropStale(at(15))).isZero();
        assertThat(messages.dropStale(late)).isEqualTo(3);

        assertThat(messages.snapshot().messages())
                .extracting(OutgoingMessage::state)
                .containsExactly(
                        new MessageState.Dropped(late, DropCause.STALE_WHILE_SENDING),
                        new MessageState.Dropped(late, DropCause.OUTCOME_UNKNOWN),
                        new MessageState.Dropped(late, DropCause.STALE),
                        new MessageState.Accepted(at(1), REFERENCE));
    }

    @Test
    void GivenAMessageOfUnknownOutcomeASecondPast15Minutes_WhenClaimed_ThenItIsDroppedWithItsOutcomeUnknown() {
        StopMessages messages = inState(State.UNKNOWN);
        Instant late = at(15).plusSeconds(1);

        assertThat(messages.claim(messageId(1), ROOM, PhoneConsent.MAY_BE_MESSAGED, late))
                .isEqualTo(Claim.DROPPED);
        assertThat(stateOf(messages)).isEqualTo(new MessageState.Dropped(late, DropCause.OUTCOME_UNKNOWN));
    }

    @Test
    void GivenAMessageDroppedWhilePending_WhenTheProviderReportsItAccepted_ThenMessageNotSending() {
        StopMessages messages = aStopMessages()
                .with(outForDelivery(1, new MessageState.Dropped(at(16), DropCause.STALE)))
                .build();

        assertRefusedUnchanged(
                messages,
                () -> messages.accepted(messageId(1), REFERENCE, at(17)),
                new MessageNotSending(messageId(1)));
    }

    @Test
    void
            GivenADeliveredMessageDroppedAsStaleWhileSending_WhenTheProviderAcceptsIt_ThenItIsAcceptedAndOpensItsRatingRequest() {
        OutgoingMessage asking = new OutgoingMessage(
                messageId(1),
                MessageType.DELIVERED,
                1,
                MOBILE,
                text(new Wording.Delivered(false, true)),
                Optional.empty(),
                true,
                TEN,
                new MessageState.Sending(at(14)));
        StopMessages messages = aStopMessages().with(asking).build();
        messages.dropStale(at(16));

        assertThat(messages.accepted(messageId(1), REFERENCE, at(17)))
                .contains(new RatingQuestion(STOP, MOBILE, at(17)));
        assertThat(stateOf(messages)).isEqualTo(new MessageState.Accepted(at(17), REFERENCE));
        assertThat(stateOf(messages).countsTowardTheDailyCap()).isTrue();
    }

    // --- acceptance and the rating question (DL §6.2)

    @Test
    void GivenADeliveredMessageThatAsksForARating_WhenAccepted_ThenItGivesTheQuestionToOpenARequestFor() {
        OutgoingMessage asking = new OutgoingMessage(
                messageId(1),
                MessageType.DELIVERED,
                1,
                MOBILE,
                text(new Wording.Delivered(false, true)),
                Optional.empty(),
                true,
                TEN,
                new MessageState.Sending(at(1)));
        StopMessages messages = aStopMessages().with(asking).build();

        assertThat(messages.accepted(messageId(1), REFERENCE, SOON)).contains(new RatingQuestion(STOP, MOBILE, SOON));
    }

    @Test
    void GivenADeliveredMessageToAnotherPhoneThanAnEarlierMessage_WhenAccepted_ThenTheQuestionGoesToItsOwnPhone() {
        OutgoingMessage asking = new OutgoingMessage(
                messageId(2),
                MessageType.DELIVERED,
                1,
                OTHER_MOBILE,
                text(new Wording.Delivered(false, true)),
                Optional.empty(),
                true,
                TEN,
                new MessageState.Sending(at(1)));
        StopMessages messages = aStopMessages()
                .with(outForDelivery(1, ACCEPTED_TODAY))
                .with(asking)
                .build();

        assertThat(messages.accepted(messageId(2), REFERENCE, SOON))
                .contains(new RatingQuestion(STOP, OTHER_MOBILE, SOON));
    }

    @Test
    void GivenAnyOtherMessage_WhenAccepted_ThenThereIsNoQuestion() {
        StopMessages messages = inState(State.SENDING);

        assertThat(messages.accepted(messageId(1), REFERENCE, SOON)).isEmpty();
    }

    // --- retention (NFR-184, ADR-108)

    @Test
    void GivenMessages_WhenTheirRetentionIsAsked_ThenItEnds90DaysAfterTheLastStateChange() {
        StopMessages messages = aStopMessages()
                .with(outForDelivery(1, new MessageState.Accepted(at(1), REFERENCE)))
                .with(plain(2, MessageType.DELIVERED, new MessageState.Dropped(at(20), DropCause.STALE)))
                .build();

        assertThat(messages.retentionEndsAt()).contains(Instant.parse("2027-09-13T07:20:00Z"));
        assertThat(aStopMessages().build().retentionEndsAt()).isEmpty();
    }
}
