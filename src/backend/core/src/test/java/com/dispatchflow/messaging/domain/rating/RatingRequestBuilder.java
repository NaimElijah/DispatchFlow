package com.dispatchflow.messaging.domain.rating;

import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.STOP;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.uuid;

import com.dispatchflow.messaging.domain.rating.RatingRequest.OneTimeReply;
import com.dispatchflow.messaging.domain.rating.RatingRequest.Rated;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The test builder of {@link RatingRequest}: {@code aRatingRequest()} is a loaded, open request whose
 * question was accepted at 10:00, with no reply yet. Every state is built as a snapshot and rebuilt
 * through {@code restore}.
 */
final class RatingRequestBuilder {

    static final RatingRequestId REQUEST = RatingRequestId.of(uuid(500));
    static final Version LOADED = Version.of(2);

    private Optional<PhoneNumber> phone = Optional.of(MOBILE);
    private Optional<Rated> rating = Optional.empty();
    private final List<ReceivedComment> comments = new ArrayList<>();
    private OneTimeReply reminder = OneTimeReply.NOT_YET;
    private OneTimeReply thankYou = OneTimeReply.NOT_YET;
    private Optional<Instant> removedAt = Optional.empty();

    private RatingRequestBuilder() {}

    static RatingRequestBuilder aRatingRequest() {
        return new RatingRequestBuilder();
    }

    RatingRequestBuilder rated(int value, Instant at) {
        this.rating = Optional.of(new Rated(Rating.of(value), at));
        this.thankYou = OneTimeReply.SENT;
        return this;
    }

    RatingRequestBuilder withComment(String text, Instant at) {
        comments.add(new ReceivedComment(Comment.cutFrom(text), at));
        return this;
    }

    RatingRequestBuilder withReminder(OneTimeReply reminder) {
        this.reminder = reminder;
        return this;
    }

    RatingRequestBuilder withThankYou(OneTimeReply thankYou) {
        this.thankYou = thankYou;
        return this;
    }

    RatingRequestBuilder withPhone(Optional<PhoneNumber> phone) {
        this.phone = phone;
        return this;
    }

    RatingRequestBuilder removed(Instant at) {
        this.phone = Optional.empty();
        this.comments.clear();
        this.removedAt = Optional.of(at);
        return this;
    }

    RatingRequestBuilder phoneRemoved(Instant at) {
        this.phone = Optional.empty();
        this.removedAt = Optional.of(at);
        return this;
    }

    RatingRequestBuilder withRemovedAt(Optional<Instant> at) {
        this.removedAt = at;
        return this;
    }

    RatingRequestSnapshot snapshot() {
        return new RatingRequestSnapshot(
                REQUEST, LOADED, STOP, phone, TEN, rating, comments, reminder, thankYou, removedAt);
    }

    RatingRequest build() {
        return RatingRequest.restore(snapshot());
    }
}
