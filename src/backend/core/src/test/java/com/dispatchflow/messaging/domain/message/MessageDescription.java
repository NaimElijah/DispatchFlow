package com.dispatchflow.messaging.domain.message;

import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.messageId;
import static com.dispatchflow.messaging.domain.MessagingFixtures.quarter;

import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.messaging.domain.MessagingFixtures;
import com.dispatchflow.messaging.domain.OutgoingMessageId;
import com.dispatchflow.messaging.domain.QuarterHour;
import com.dispatchflow.messaging.domain.Wording;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What a test sees of one {@link OutgoingMessage}, read through its getters: a value that compares
 * by every part, which the entity doesn't. Tests compare a stop's messages before and after a
 * command, or with the messages they expect, through it.
 *
 * @param id the message's ID
 * @param type what it says
 * @param sequence its place among the stop's messages of its type
 * @param phone the phone it goes to
 * @param text its text
 * @param arrivalSent the arrival it gives, if any
 * @param asksForRating whether it asks for a rating
 * @param triggeredAt when its trigger was recorded
 * @param state where it is in its lifecycle
 */
public record MessageDescription(
        OutgoingMessageId id,
        MessageType type,
        int sequence,
        PhoneNumber phone,
        MessageText text,
        Optional<QuarterHour> arrivalSent,
        boolean asksForRating,
        Instant triggeredAt,
        MessageState state) {

    /**
     * Describes a message.
     *
     * @param message the message
     * @return what its getters give
     */
    public static MessageDescription of(OutgoingMessage message) {
        return new MessageDescription(
                message.id(),
                message.type(),
                message.sequence(),
                message.phone(),
                message.text(),
                message.arrivalSent(),
                message.asksForRating(),
                message.triggeredAt(),
                message.state());
    }

    /**
     * Describes every message of a stop.
     *
     * @param messages the stop's messages
     * @return each message's description, in the stop's order
     */
    public static List<MessageDescription> everyMessageOf(StopMessages messages) {
        return messages.messages().stream().map(MessageDescription::of).toList();
    }

    /**
     * The "out for delivery" that {@link StopMessagesBuilder#withOutForDelivery} adds: triggered at
     * 10:00 for 11:00, to {@code MOBILE}, in the state given.
     *
     * @param n the message's number, as {@code MessagingFixtures.messageId} takes it
     * @param state its state
     * @return its description
     */
    static MessageDescription outForDelivery(int n, MessageState state) {
        return new MessageDescription(
                messageId(n),
                MessageType.OUT_FOR_DELIVERY,
                1,
                MOBILE,
                MessagingFixtures.text(new Wording.OutForDelivery(quarter(60))),
                Optional.of(quarter(60)),
                false,
                TEN,
                state);
    }
}
