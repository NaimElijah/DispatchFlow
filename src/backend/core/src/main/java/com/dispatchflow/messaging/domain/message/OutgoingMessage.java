package com.dispatchflow.messaging.domain.message;

import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.messaging.domain.OutgoingMessageId;
import com.dispatchflow.messaging.domain.QuarterHour;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One message to a stop's recipient, an entity inside {@link StopMessages} (DL §4.6, §17). It is
 * immutable: {@code StopMessages} replaces it when its state moves. Its type and sequence identify
 * it, so it is sent at most once (NFR-191).
 *
 * <p>It keeps the phone it goes to, chosen by FR-348 when it was triggered, so a later trigger that
 * names another phone changes neither where it is sent nor where it was sent (ADR-117).
 *
 * <p>Its phone and its text are personal data (NFR-193), so {@link #toString()} leaves both out.
 *
 * @param id the message's local ID, DispatchFlow's reference to the provider (NFR-198)
 * @param type what the message says
 * @param sequence 1, or for a second "time changed" 2 (NFR-191, FR-384)
 * @param phone the phone it goes to, an Israeli mobile when it was triggered (FR-348, FR-378)
 * @param text the text, written when it was triggered
 * @param arrivalSent the rounded arrival it gives, for "out for delivery" and "time changed" only
 * @param asksForRating whether it is a "delivered" message that asks for a rating (FR-356)
 * @param triggeredAt when its trigger was recorded, from which NFR-190's 15 minutes run
 * @param state where it is in its lifecycle (DL §9.5)
 */
public record OutgoingMessage(
        OutgoingMessageId id,
        MessageType type,
        int sequence,
        PhoneNumber phone,
        MessageText text,
        Optional<QuarterHour> arrivalSent,
        boolean asksForRating,
        Instant triggeredAt,
        MessageState state)
        implements PersonalData {

    /** Checks every part is present. The rules between parts are {@link StopMessages}'. */
    public OutgoingMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(arrivalSent, "arrivalSent");
        Objects.requireNonNull(triggeredAt, "triggeredAt");
        Objects.requireNonNull(state, "state");
    }

    /**
     * The same message in another state.
     *
     * @param next the new state
     * @return the message
     */
    OutgoingMessage in(MessageState next) {
        return new OutgoingMessage(id, type, sequence, phone, text, arrivalSent, asksForRating, triggeredAt, next);
    }

    /**
     * A form that leaves the phone and the text out.
     *
     * @return the message without its phone and text
     */
    @Override
    public String toString() {
        return "OutgoingMessage[id=" + id + ", type=" + type + ", sequence=" + sequence + ", phone=redacted"
                + ", text=redacted, arrivalSent=" + arrivalSent + ", asksForRating=" + asksForRating
                + ", triggeredAt=" + triggeredAt + ", state=" + state + "]";
    }
}
