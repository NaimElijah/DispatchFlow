package com.dispatchflow.messaging.domain.message;

import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.messaging.domain.OutgoingMessageId;
import com.dispatchflow.messaging.domain.QuarterHour;
import com.dispatchflow.messaging.domain.message.MessageState.DropCause;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One message to a stop's recipient, an entity inside {@link StopMessages} (DL §4.6, §17). Only its
 * state moves, and only {@code StopMessages} moves it. Its type and sequence identify it, so it is
 * sent at most once (NFR-191).
 *
 * <p>It keeps the phone it goes to, chosen by FR-348 when it was triggered, so a later trigger that
 * names another phone changes neither where it is sent nor where it was sent (ADR-117).
 *
 * <p>It is mapped for JPA (ADR-126): its {@link MessageState} is stored as a kind, the time that
 * state was reached, and the one field each of two states has. Its phone and its text are personal
 * data (NFR-193), so {@link #toString()} leaves both out.
 */
@Entity
@Table(schema = "messaging", name = "outgoing_message")
public class OutgoingMessage implements PersonalData {

    /** Which {@link MessageState} a message is in. */
    private enum StateKind {
        PENDING,
        SENDING,
        ACCEPTED,
        UNKNOWN,
        DROPPED
    }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private MessageType type;

    private int sequence;

    private PhoneNumber phone;

    private MessageText text;

    private @Nullable QuarterHour arrivalSent;

    private boolean asksForRating;

    private Instant triggeredAt;

    @Enumerated(EnumType.STRING)
    private StateKind state;

    private Instant stateSince;

    private @Nullable ProviderReference providerReference;

    @Enumerated(EnumType.STRING)
    private @Nullable DropCause dropCause;

    /** For Hibernate, which sets the fields from the stored row. */
    protected OutgoingMessage() {}

    /**
     * Creates a message. The rules between its parts are {@link StopMessages}'.
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
     * @throws NullPointerException naming the part that is missing
     */
    OutgoingMessage(
            OutgoingMessageId id,
            MessageType type,
            int sequence,
            PhoneNumber phone,
            MessageText text,
            Optional<QuarterHour> arrivalSent,
            boolean asksForRating,
            Instant triggeredAt,
            MessageState state) {
        this.id = Objects.requireNonNull(id, "id").value();
        this.type = Objects.requireNonNull(type, "type");
        this.sequence = sequence;
        this.phone = Objects.requireNonNull(phone, "phone");
        this.text = Objects.requireNonNull(text, "text");
        this.arrivalSent = Objects.requireNonNull(arrivalSent, "arrivalSent").orElse(null);
        this.asksForRating = asksForRating;
        this.triggeredAt = Objects.requireNonNull(triggeredAt, "triggeredAt");
        moveTo(Objects.requireNonNull(state, "state"));
    }

    /**
     * The message's ID.
     *
     * @return its local ID, DispatchFlow's reference to the provider (NFR-198)
     */
    public OutgoingMessageId id() {
        return OutgoingMessageId.of(id);
    }

    /**
     * What the message says.
     *
     * @return its type
     */
    public MessageType type() {
        return type;
    }

    /**
     * Its place among the stop's messages of its type.
     *
     * @return 1, or for a second "time changed" 2 (NFR-191, FR-384)
     */
    public int sequence() {
        return sequence;
    }

    /**
     * The phone it goes to, chosen when it was triggered (FR-348, ADR-117).
     *
     * @return the phone
     */
    public PhoneNumber phone() {
        return phone;
    }

    /**
     * The text, written when it was triggered.
     *
     * @return the text
     */
    public MessageText text() {
        return text;
    }

    /**
     * The rounded arrival it gives (FR-351, FR-353).
     *
     * @return the arrival, for "out for delivery" and "time changed" only
     */
    public Optional<QuarterHour> arrivalSent() {
        return Optional.ofNullable(arrivalSent);
    }

    /**
     * Whether it asks for a rating (FR-356).
     *
     * @return {@code true} for a "delivered" message that asks
     */
    public boolean asksForRating() {
        return asksForRating;
    }

    /**
     * When its trigger was recorded, from which NFR-190's 15 minutes run.
     *
     * @return the time
     */
    public Instant triggeredAt() {
        return triggeredAt;
    }

    /**
     * Where it is in its lifecycle (DL §9.5).
     *
     * @return the state
     */
    public MessageState state() {
        return switch (state) {
            case PENDING -> new MessageState.Pending(stateSince);
            case SENDING -> new MessageState.Sending(stateSince);
            case ACCEPTED ->
                new MessageState.Accepted(
                        stateSince, Objects.requireNonNull(providerReference, "an accepted message has a reference"));
            case UNKNOWN -> new MessageState.Unknown(stateSince);
            case DROPPED ->
                new MessageState.Dropped(
                        stateSince, Objects.requireNonNull(dropCause, "a dropped message has a cause"));
        };
    }

    /**
     * Moves the message to another state, in place.
     *
     * @param next the new state
     */
    void moveTo(MessageState next) {
        stateSince = next.since();
        providerReference = null;
        dropCause = null;
        state = switch (next) {
            case MessageState.Pending pending -> StateKind.PENDING;
            case MessageState.Sending sending -> StateKind.SENDING;
            case MessageState.Accepted accepted -> {
                providerReference = accepted.reference();
                yield StateKind.ACCEPTED;
            }
            case MessageState.Unknown unknown -> StateKind.UNKNOWN;
            case MessageState.Dropped dropped -> {
                dropCause = dropped.cause();
                yield StateKind.DROPPED;
            }
        };
    }

    /**
     * A copy that shares nothing with this message, for a {@link StopMessagesSnapshot}; it goes with
     * the snapshots (ADR-126).
     *
     * @return the copy, which no persistence context manages
     */
    OutgoingMessage copy() {
        return new OutgoingMessage(
                id(), type, sequence, phone, text, arrivalSent(), asksForRating, triggeredAt, state());
    }

    /**
     * Whether another message has the same ID and the same parts, as the record this class replaced
     * compared them, so that two {@link StopMessagesSnapshot}s compare by their messages' contents.
     *
     * @param other the object to compare
     * @return {@code true} when every part is equal
     */
    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof OutgoingMessage that
                && id.equals(that.id)
                && type == that.type
                && sequence == that.sequence
                && phone.equals(that.phone)
                && text.equals(that.text)
                && Objects.equals(arrivalSent, that.arrivalSent)
                && asksForRating == that.asksForRating
                && triggeredAt.equals(that.triggeredAt)
                && state().equals(that.state());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, sequence, phone, text, arrivalSent, asksForRating, triggeredAt, state());
    }

    /**
     * A form that leaves the phone and the text out.
     *
     * @return the message without its phone and text
     */
    @Override
    public String toString() {
        return "OutgoingMessage[id=" + id() + ", type=" + type + ", sequence=" + sequence + ", phone=redacted"
                + ", text=redacted, arrivalSent=" + arrivalSent() + ", asksForRating=" + asksForRating
                + ", triggeredAt=" + triggeredAt + ", state=" + state() + "]";
    }
}
