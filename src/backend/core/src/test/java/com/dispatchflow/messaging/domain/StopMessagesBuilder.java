package com.dispatchflow.messaging.domain;

import static com.dispatchflow.messaging.domain.MessagingFixtures.DISPATCH;
import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.STOP;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.messageId;
import static com.dispatchflow.messaging.domain.MessagingFixtures.quarter;
import static com.dispatchflow.messaging.domain.MessagingFixtures.text;

import com.dispatchflow.shared.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The test builder of {@link StopMessages}: {@code aStopMessages()} is a loaded stop with no
 * message. Every state is built as a snapshot and rebuilt through {@code restore}; the messages
 * {@link #outForDelivery} and {@link #plain} make go to {@link MessagingFixtures#MOBILE}.
 */
final class StopMessagesBuilder {

    static final Version LOADED = Version.of(3);

    private final List<OutgoingMessage> messages = new ArrayList<>();

    private StopMessagesBuilder() {}

    static StopMessagesBuilder aStopMessages() {
        return new StopMessagesBuilder();
    }

    /** "Out for delivery", sent at 10:00 for 11:00, accepted. */
    static OutgoingMessage outForDelivery(int n, MessageState state) {
        return new OutgoingMessage(
                messageId(n),
                MessageType.OUT_FOR_DELIVERY,
                1,
                MOBILE,
                text(new Wording.OutForDelivery(quarter(60))),
                Optional.of(quarter(60)),
                false,
                TEN,
                state);
    }

    /** A message of a type that gives no arrival, triggered at 10:00. */
    static OutgoingMessage plain(int n, MessageType type, MessageState state) {
        Wording wording = switch (type) {
            case ARRIVING_SOON -> new Wording.ArrivingSoon();
            case DELIVERY_CHANGED -> new Wording.DeliveryChanged(DISPATCH);
            case MISSED_YOU -> new Wording.MissedYou(DISPATCH);
            case DELIVERED -> new Wording.Delivered(false, false);
            case OUT_FOR_DELIVERY, TIME_CHANGED -> throw new AssertionError("gives an arrival");
        };
        return new OutgoingMessage(messageId(n), type, 1, MOBILE, text(wording), Optional.empty(), false, TEN, state);
    }

    static MessageState pendingSinceTen() {
        return new MessageState.Pending(TEN);
    }

    StopMessagesBuilder with(OutgoingMessage message) {
        messages.add(message);
        return this;
    }

    StopMessagesSnapshot snapshot() {
        return new StopMessagesSnapshot(STOP, LOADED, messages);
    }

    StopMessages build() {
        return StopMessages.restore(snapshot());
    }
}
