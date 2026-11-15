package com.dispatchflow.messaging.domain.message;

import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.OTHER_MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.STOP;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.at;
import static com.dispatchflow.messaging.domain.MessagingFixtures.messageId;
import static com.dispatchflow.messaging.domain.MessagingFixtures.quarter;
import static com.dispatchflow.messaging.domain.MessagingFixtures.text;
import static com.dispatchflow.messaging.domain.message.StopMessagesBuilder.aStopMessages;
import static com.dispatchflow.messaging.domain.message.StopMessagesBuilder.outForDelivery;
import static com.dispatchflow.messaging.domain.message.StopMessagesBuilder.plain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.messaging.domain.MessagingFixtures;
import com.dispatchflow.messaging.domain.Wording;
import com.dispatchflow.messaging.domain.message.MessageState.DropCause;
import com.dispatchflow.shared.StoredStateInvalid;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link StopMessages#restore}: one crafted snapshot per invariant at rest, and round trips. */
class StopMessagesRestoreTest {

    private static final ProviderReference REFERENCE = new ProviderReference("provider-1");
    private static final MessageState ACCEPTED = new MessageState.Accepted(at(1), REFERENCE);

    private static OutgoingMessage timeChanged(int n, int sequence) {
        return new OutgoingMessage(
                messageId(n),
                MessageType.TIME_CHANGED,
                sequence,
                MOBILE,
                text(new Wording.TimeChanged(quarter(90))),
                Optional.of(quarter(90)),
                false,
                TEN,
                ACCEPTED);
    }

    static Stream<StopMessagesBuilder> builderStates() {
        return Stream.of(
                aStopMessages(),
                aStopMessages().with(outForDelivery(1, new MessageState.Pending(TEN))),
                aStopMessages()
                        .with(outForDelivery(1, ACCEPTED))
                        .with(timeChanged(2, 1))
                        .with(timeChanged(3, 2))
                        .with(plain(4, MessageType.ARRIVING_SOON, new MessageState.Unknown(at(2))))
                        .with(plain(5, MessageType.DELIVERY_CHANGED, new MessageState.Sending(at(3))))
                        .with(plain(6, MessageType.DELIVERED, new MessageState.Dropped(at(4), DropCause.OPTED_OUT))));
    }

    @ParameterizedTest
    @MethodSource("builderStates")
    void GivenEachBuilderState_WhenItsSnapshotIsRestored_ThenTheSnapshotRoundTrips(StopMessagesBuilder builder) {
        StopMessages messages = builder.build();

        assertThat(StopMessages.restore(messages.snapshot()).snapshot()).isEqualTo(builder.snapshot());
        assertThat(messages.version()).isEqualTo(StopMessagesBuilder.LOADED);
        assertThat(messages.pendingEvents()).isEmpty();
    }

    static Stream<Arguments> damagedSnapshots() {
        OutgoingMessage delivered = plain(2, MessageType.DELIVERED, ACCEPTED);
        return Stream.of(
                Arguments.of(
                        "two messages with one ID",
                        aStopMessages()
                                .with(outForDelivery(1, ACCEPTED))
                                .with(plain(1, MessageType.ARRIVING_SOON, ACCEPTED)),
                        "each message has its own ID"),
                Arguments.of(
                        "a second out for delivery",
                        aStopMessages().with(outForDelivery(1, ACCEPTED)).with(outForDelivery(2, ACCEPTED)),
                        "each type is numbered from 1, up to its most per stop (NFR-191, FR-384)"),
                Arguments.of(
                        "a time changed numbered 2 with no 1",
                        aStopMessages().with(outForDelivery(1, ACCEPTED)).with(timeChanged(2, 2)),
                        "each type is numbered from 1, up to its most per stop (NFR-191, FR-384)"),
                Arguments.of(
                        "a third time changed",
                        aStopMessages()
                                .with(outForDelivery(1, ACCEPTED))
                                .with(timeChanged(2, 1))
                                .with(timeChanged(3, 2))
                                .with(timeChanged(4, 3)),
                        "each type is numbered from 1, up to its most per stop (NFR-191, FR-384)"),
                Arguments.of(
                        "an arriving soon that gives an arrival",
                        aStopMessages()
                                .with(new OutgoingMessage(
                                        messageId(1),
                                        MessageType.ARRIVING_SOON,
                                        1,
                                        MOBILE,
                                        text(new Wording.ArrivingSoon()),
                                        Optional.of(quarter(60)),
                                        false,
                                        TEN,
                                        ACCEPTED)),
                        "only out for delivery and time changed give an arrival"),
                Arguments.of(
                        "an out for delivery with no arrival",
                        aStopMessages()
                                .with(new OutgoingMessage(
                                        messageId(1),
                                        MessageType.OUT_FOR_DELIVERY,
                                        1,
                                        MOBILE,
                                        text(new Wording.ArrivingSoon()),
                                        Optional.empty(),
                                        false,
                                        TEN,
                                        ACCEPTED)),
                        "only out for delivery and time changed give an arrival"),
                Arguments.of(
                        "a missed you that asks for a rating",
                        aStopMessages()
                                .with(new OutgoingMessage(
                                        messageId(1),
                                        MessageType.MISSED_YOU,
                                        1,
                                        MOBILE,
                                        text(new Wording.ArrivingSoon()),
                                        Optional.empty(),
                                        true,
                                        TEN,
                                        ACCEPTED)),
                        "only a delivered message asks for a rating"),
                Arguments.of(
                        "a time changed before any arrival",
                        aStopMessages().with(timeChanged(1, 1)),
                        "a time changed message follows one that gave an arrival (FR-353)"),
                Arguments.of(
                        "a pending message pending since another time",
                        aStopMessages().with(outForDelivery(1, new MessageState.Pending(at(1)))),
                        "a pending message is pending since its trigger"),
                Arguments.of(
                        "both outcome messages",
                        aStopMessages().with(delivered).with(plain(3, MessageType.MISSED_YOU, ACCEPTED)),
                        "a stop gets one outcome message, for its first outcome (FR-374)"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedSnapshots")
    void GivenASnapshotBreakingAnInvariantAtRest_WhenRestored_ThenStoredStateInvalid(
            String broken, StopMessagesBuilder builder, String detail) {
        assertThatThrownBy(builder::build).isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
            assertThat(invalid.subject()).isEqualTo("StopMessages");
            assertThat(invalid.detail()).isEqualTo(detail);
        });
    }

    @Test
    void GivenAStoredMessageWithNoPhone_WhenRebuilt_ThenItIsRefusedBeforeTheStopIsRestored() {
        assertThatThrownBy(() -> new OutgoingMessage(
                        messageId(1),
                        MessageType.ARRIVING_SOON,
                        1,
                        null,
                        text(new Wording.ArrivingSoon()),
                        Optional.empty(),
                        false,
                        TEN,
                        ACCEPTED))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("phone");
    }

    @Test
    void GivenMessagesToTwoPhones_WhenTheSnapshotIsRestored_ThenEachKeepsItsOwnPhone() {
        StopMessagesBuilder builder = aStopMessages()
                .with(outForDelivery(1, ACCEPTED))
                .with(new OutgoingMessage(
                        messageId(2),
                        MessageType.MISSED_YOU,
                        1,
                        OTHER_MOBILE,
                        text(new Wording.MissedYou(MessagingFixtures.DISPATCH)),
                        Optional.empty(),
                        false,
                        TEN,
                        ACCEPTED));

        assertThat(builder.build().snapshot().messages())
                .extracting(OutgoingMessage::phone)
                .containsExactly(MOBILE, OTHER_MOBILE);
    }

    @Test
    void GivenAStopWithMessages_WhenItsSnapshotIsPrinted_ThenNeitherThePhoneNorAnyTextShows() {
        StopMessages messages =
                aStopMessages().with(outForDelivery(1, ACCEPTED)).build();

        assertThat(messages.snapshot().toString())
                .doesNotContain("345")
                .doesNotContain("Shefa")
                .contains(STOP.toString());
        assertThat(messages.toString()).doesNotContain("345").doesNotContain("Shefa");
        assertThat(List.of(messages.snapshot().messages().get(0)).toString()).doesNotContain("Shefa");
    }
}
