package com.dispatchflow.messaging.domain.rating;

import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.STOP;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.rating.RatingRequestBuilder.REQUEST;

import com.dispatchflow.messaging.domain.rating.RatingRequest.OneTimeReply;
import com.dispatchflow.messaging.domain.rating.RatingRequest.Rated;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The state a test expects of {@link RatingRequestBuilder}'s request after a command, written out
 * part by part rather than reached through the commands under test. {@code expectedRequest()} is
 * the request the builder starts from: open, its question to {@code MOBILE} accepted at 10:00.
 */
final class ExpectedRatingRequest {

    private Optional<PhoneNumber> phone = Optional.of(MOBILE);
    private Optional<Rated> rating = Optional.empty();
    private final List<ReceivedComment> comments = new ArrayList<>();
    private OneTimeReply reminder = OneTimeReply.NOT_YET;
    private OneTimeReply thankYou = OneTimeReply.NOT_YET;
    private Optional<Instant> removedAt = Optional.empty();

    private ExpectedRatingRequest() {}

    static ExpectedRatingRequest expectedRequest() {
        return new ExpectedRatingRequest();
    }

    /** Rated, with the thank-you sent. */
    ExpectedRatingRequest rated(int value, Instant at) {
        this.rating = Optional.of(new Rated(Rating.of(value), at));
        this.thankYou = OneTimeReply.SENT;
        return this;
    }

    ExpectedRatingRequest withComment(String text, Instant at) {
        comments.add(new ReceivedComment(Comment.cutFrom(text), at));
        return this;
    }

    ExpectedRatingRequest withReminder(OneTimeReply reminder) {
        this.reminder = reminder;
        return this;
    }

    /** The phone and every comment removed. */
    ExpectedRatingRequest removed(Instant at) {
        this.phone = Optional.empty();
        this.comments.clear();
        this.removedAt = Optional.of(at);
        return this;
    }

    /** The phone removed, the comments kept. */
    ExpectedRatingRequest phoneRemoved(Instant at) {
        this.phone = Optional.empty();
        this.removedAt = Optional.of(at);
        return this;
    }

    RatingRequestDescription described() {
        return new RatingRequestDescription(
                REQUEST, STOP, phone, TEN, rating, List.copyOf(comments), reminder, thankYou, removedAt);
    }
}
