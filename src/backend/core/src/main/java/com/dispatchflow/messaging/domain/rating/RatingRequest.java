package com.dispatchflow.messaging.domain.rating;

import com.dispatchflow.messaging.domain.MessagingError.RetentionNotOver;
import com.dispatchflow.messaging.domain.MessagingRetention;
import com.dispatchflow.messaging.domain.OptOutStatus;
import com.dispatchflow.messaging.domain.RatingQuestion;
import com.dispatchflow.messaging.domain.ReplyMessage;
import com.dispatchflow.messaging.domain.SendingConditions;
import com.dispatchflow.messaging.events.RatingReceived;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A delivered stop's question "how was it?", and the replies that answer it (DL §4.6). It opens when
 * the provider accepts the "delivered" message that asks it, and answers replies for 48 hours
 * (FR-357): the first rating counts, every reply other than a bare rating or an opt-out is kept as
 * a comment, even after the rating (FR-360), a rating gets one thank-you (FR-358), and the first
 * reply that is not a rating, before the rating, gets one format reminder (FR-359). A reply outside
 * the 48 hours is discarded (FR-379). Once it keeps 20 comments, it ignores every later reply
 * (ADR-108).
 *
 * <p>It is <em>closed</em> from 48 hours after the question was accepted, which is a matter of time
 * and is not stored ({@link #statusAt}). Its phone is removed 90 days after the question was
 * accepted, and each comment 90 days after its own reply was received; its rating stays (NFR-184,
 * NFR-193). Every method checks all its rules before it changes anything, and every change ends by
 * checking the invariants at rest.
 *
 * <p>It is mapped for JPA (ADR-126): its key is its ID's UUID, its rating two nullable columns, and
 * its comments an ordered element collection.
 */
@Entity
@Table(schema = "messaging", name = "rating_request")
public class RatingRequest extends AggregateRoot implements PersonalData {

    /** How long after its question was accepted a reply answers it (FR-357). */
    public static final Duration ANSWER_WINDOW = Duration.ofHours(48);

    /**
     * The most comments a request keeps; a starting value the owner set, not a requirement, so a
     * phone can't fill a request without end (ADR-108).
     */
    public static final int MOST_COMMENTS = 20;

    /** What became of a message a request sends once (FR-358, FR-359). */
    public enum OneTimeReply {
        /** Not due yet. */
        NOT_YET,
        /** Due, and sent. */
        SENT,
        /** Due while messages could not go to the phone, so never sent (FR-350, FR-363). */
        SKIPPED
    }

    /**
     * The request's state at a time (DL §9.7).
     */
    public enum Status {
        /** Within its 48 hours, not rated. */
        OPEN,
        /** Within its 48 hours, rated; it still keeps comments (FR-360). */
        RATED,
        /** Past its 48 hours; a rating given stays. */
        CLOSED
    }

    /**
     * A delivery's rating, stored in its request's columns.
     *
     * @param rating the rating
     * @param at when its reply was received
     */
    @Embeddable
    public record Rated(Rating rating, Instant at) {

        /** Checks every part is present. */
        public Rated {
            Objects.requireNonNull(rating, "rating");
            Objects.requireNonNull(at, "at");
        }
    }

    @Id
    private UUID id;

    private StopId stop;

    private Instant acceptedAt;

    private @Nullable PhoneNumber phone;

    @Embedded
    @AttributeOverride(name = "rating", column = @Column(name = "rating"))
    @AttributeOverride(name = "at", column = @Column(name = "rated_at"))
    private @Nullable Rated rating;

    @ElementCollection
    @CollectionTable(
            schema = "messaging",
            name = "rating_request_comment",
            joinColumns = @JoinColumn(name = "rating_request_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<ReceivedComment> comments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private OneTimeReply reminder;

    @Enumerated(EnumType.STRING)
    private OneTimeReply thankYou;

    private @Nullable Instant removedAt;

    /** For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored}. */
    protected RatingRequest() {}

    private RatingRequest(RatingRequestId id, RatingQuestion question) {
        this.id = id.value();
        this.stop = question.stop();
        this.acceptedAt = question.acceptedAt();
        this.phone = question.phone();
        this.reminder = OneTimeReply.NOT_YET;
        this.thankYou = OneTimeReply.NOT_YET;
    }

    /**
     * Opens the request when the provider accepts a "delivered" message that asks for a rating (DL
     * §6.2).
     *
     * @param id the new request's ID
     * @param question the accepted question
     * @return the request, open, never saved
     */
    public static RatingRequest open(RatingRequestId id, RatingQuestion question) {
        return new RatingRequest(Objects.requireNonNull(id, "id"), Objects.requireNonNull(question, "question"));
    }

    /**
     * Checks the request as Hibernate loaded it against the invariants that hold at rest (ADR-126
     * decision 8): it has its phone exactly until it is removed; it keeps at most 20 comments; its
     * rating and comments were received within its 48 hours; and its thank-you is due exactly when
     * it is rated.
     *
     * @throws StoredStateInvalid when a stored list has a gap in its positions, or the stored state
     *     breaks one of them
     */
    @PostLoad
    void checkStored() {
        brokenRule().ifPresent(broken -> {
            throw new StoredStateInvalid(RatingRequest.class, broken);
        });
    }

    /**
     * A reply from the request's phone, the latest request sent to it (FR-371), answers it. Within
     * the 48 hours after the question was accepted, ends included:
     *
     * <ul>
     *   <li>the first rating rates the delivery, raises {@link RatingReceived}, and gets the one
     *       thank-you (FR-357, FR-358, FR-361); a later rating is not counted, and gets no answer;
     *   <li>every reply other than a bare rating or an opt-out word is kept as a comment, after the
     *       rating too (FR-360), and "8 thanks" is both;
     *   <li>before the rating, the first reply that is neither a rating nor an opt-out word gets the
     *       one format reminder (FR-359).
     * </ul>
     *
     * An answer goes only while messages may go to the phone (FR-350, FR-363); when they can't,
     * it is skipped, not kept for later. A reply outside the 48 hours is discarded (FR-379). An
     * opt-out word changes nothing here; the opt-out history takes it ({@link OptOutStatus}). Once
     * the request keeps 20 comments, every later reply is ignored and gets no answer (ADR-108).
     *
     * @param reply the reply, read by {@link Reply#parse}
     * @param conditions the settings and the phone's standing now
     * @param receivedAt when the reply was received
     * @return what it counted for, and the answer due
     */
    public Answer answer(Reply reply, SendingConditions conditions, Instant receivedAt) {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if (!answersAt(receivedAt)) {
            return new Answer(Answer.Counted.DISCARDED, Optional.empty());
        }
        if (comments.size() >= MOST_COMMENTS) {
            return new Answer(Answer.Counted.NOTHING, Optional.empty());
        }
        OneTimeReply allowed = conditions.mayMessage() ? OneTimeReply.SENT : OneTimeReply.SKIPPED;
        return switch (reply) {
            case Reply.OptOutWord optOut -> new Answer(Answer.Counted.NOTHING, Optional.empty());
            case Reply.BlankReply blank -> notARating(Optional.empty(), allowed, receivedAt);
            case Reply.TextReply(Comment comment) -> notARating(Optional.of(comment), allowed, receivedAt);
            case Reply.RatingReply(Rating given, Optional<Comment> comment) ->
                rated(given, comment, allowed, receivedAt);
        };
    }

    /**
     * Removes the request's personal data as each piece's retention ends (NFR-184, NFR-193): the
     * phone 90 days after the question was accepted, and each comment 90 days after its own reply
     * was received. The rating stays, for the day's figures (FR-362). With nothing left to remove it
     * changes nothing, so the nightly retention job may call it again.
     *
     * @param now when the retention job runs
     * @throws RuleViolation with {@link RetentionNotOver} when personal data is left and none of it
     *     is due yet
     */
    public void removePersonalData(Instant now) {
        Objects.requireNonNull(now, "now");
        boolean phoneDue = phone != null && !now.isBefore(MessagingRetention.endOf(acceptedAt));
        Predicate<ReceivedComment> commentDue =
                comment -> !now.isBefore(MessagingRetention.endOf(comment.receivedAt()));
        if (!phoneDue && comments.stream().noneMatch(commentDue)) {
            if (phone == null && comments.isEmpty()) {
                return;
            }
            throw new RuleViolation(new RetentionNotOver());
        }
        comments.removeIf(commentDue);
        if (phoneDue) {
            phone = null;
            removedAt = now;
        }
        requireInvariants();
    }

    /**
     * The request's state at a time (DL §9.7).
     *
     * @param now the time asked about
     * @return closed after the 48 hours; otherwise rated or open
     */
    public Status statusAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isAfter(acceptedAt.plus(ANSWER_WINDOW))) {
            return Status.CLOSED;
        }
        return rating == null ? Status.OPEN : Status.RATED;
    }

    /**
     * The request's ID.
     *
     * @return the ID
     */
    public RatingRequestId id() {
        return RatingRequestId.of(id);
    }

    /**
     * The delivered stop the request is about.
     *
     * @return the stop
     */
    public StopId stop() {
        return stop;
    }

    /**
     * The phone the question went to, until its retention ends (NFR-184).
     *
     * @return the phone; empty once removed
     */
    public Optional<PhoneNumber> phone() {
        return Optional.ofNullable(phone);
    }

    /**
     * When the provider accepted the question, from which the 48 hours run (FR-357).
     *
     * @return the time
     */
    public Instant acceptedAt() {
        return acceptedAt;
    }

    /**
     * The delivery's rating, once given; it stays when the personal data goes (FR-362).
     *
     * @return the rating and when its reply was received; empty before the first rating
     */
    public Optional<Rated> rating() {
        return Optional.ofNullable(rating);
    }

    /**
     * The replies kept as comments (FR-360).
     *
     * @return an unmodifiable copy, at most 20, in the order received
     */
    public List<ReceivedComment> comments() {
        return List.copyOf(comments);
    }

    /**
     * What became of the one format reminder (FR-359).
     *
     * @return not yet due, sent or skipped
     */
    public OneTimeReply reminder() {
        return reminder;
    }

    /**
     * What became of the one thank-you (FR-358).
     *
     * @return not yet due, sent or skipped
     */
    public OneTimeReply thankYou() {
        return thankYou;
    }

    /**
     * When the phone was removed (NFR-184); each comment goes on its own date.
     *
     * @return the time; empty while the phone is kept
     */
    public Optional<Instant> removedAt() {
        return Optional.ofNullable(removedAt);
    }

    /**
     * A form that leaves the phone and the comments out.
     *
     * @return the request without personal data
     */
    @Override
    public String toString() {
        return "RatingRequest[id=" + id() + ", stop=" + stop + ", phone=" + (phone == null ? "none" : "redacted")
                + ", acceptedAt=" + acceptedAt + ", rating=" + rating + ", comments=" + comments.size()
                + ", reminder=" + reminder + ", thankYou=" + thankYou + ", removedAt=" + removedAt + "]";
    }

    private boolean answersAt(Instant receivedAt) {
        return removedAt == null
                && !receivedAt.isBefore(acceptedAt)
                && !receivedAt.isAfter(acceptedAt.plus(ANSWER_WINDOW));
    }

    private Answer notARating(Optional<Comment> comment, OneTimeReply allowed, Instant receivedAt) {
        boolean remind = rating == null && reminder == OneTimeReply.NOT_YET;
        keep(comment, receivedAt);
        if (remind) {
            reminder = allowed;
        }
        requireInvariants();
        Answer.Counted counted = comment.isPresent() ? Answer.Counted.COMMENT : Answer.Counted.NOTHING;
        return new Answer(counted, dueIf(remind && allowed == OneTimeReply.SENT, ReplyMessage.FORMAT_REMINDER));
    }

    private Answer rated(Rating given, Optional<Comment> comment, OneTimeReply allowed, Instant receivedAt) {
        boolean first = rating == null;
        if (first) {
            rating = new Rated(given, receivedAt);
            thankYou = allowed;
        }
        keep(comment, receivedAt);
        requireInvariants();
        if (first) {
            raise(
                    RatingReceived.class,
                    id,
                    eventId -> new RatingReceived(eventId, receivedAt, id(), stop, given.value()));
        }
        Answer.Counted counted;
        if (first) {
            counted = comment.isPresent() ? Answer.Counted.RATING_AND_COMMENT : Answer.Counted.RATING;
        } else {
            counted = comment.isPresent() ? Answer.Counted.COMMENT : Answer.Counted.NOTHING;
        }
        return new Answer(counted, dueIf(first && allowed == OneTimeReply.SENT, ReplyMessage.THANK_YOU));
    }

    private void keep(Optional<Comment> comment, Instant receivedAt) {
        comment.ifPresent(kept -> comments.add(new ReceivedComment(kept, receivedAt)));
    }

    private static Optional<ReplyMessage> dueIf(boolean due, ReplyMessage message) {
        return due ? Optional.of(message) : Optional.empty();
    }

    /**
     * Checks the state a command left against every invariant at rest (ADR-126 decision 11); a
     * broken one here is a bug in the command, not a refusal, and the error rolls its transaction
     * back.
     */
    private void requireInvariants() {
        brokenRule().ifPresent(broken -> {
            throw new AssertionError("a rating request command left a state that breaks: " + broken);
        });
    }

    /** The one check of the invariants at rest, shared by the load check and every command. */
    private Optional<String> brokenRule() {
        if (StoredLists.anyHasGap(comments)) {
            return Optional.of(StoredLists.GAP);
        }
        if ((removedAt != null) == (phone != null)) {
            return Optional.of("a rating request has its phone exactly until it is removed");
        }
        if (comments.size() > MOST_COMMENTS) {
            return Optional.of("a rating request keeps at most 20 comments");
        }
        Instant end = acceptedAt.plus(ANSWER_WINDOW);
        boolean outside = Stream.concat(
                        Stream.ofNullable(rating).map(Rated::at),
                        comments.stream().map(ReceivedComment::receivedAt))
                .anyMatch(at -> at.isBefore(acceptedAt) || at.isAfter(end));
        if (outside) {
            return Optional.of("a rating and comments are received within the 48 hours (FR-357, FR-360)");
        }
        if ((rating != null) == (thankYou == OneTimeReply.NOT_YET)) {
            return Optional.of("a rating request's thank-you is due exactly when it is rated (FR-358)");
        }
        return Optional.empty();
    }
}
