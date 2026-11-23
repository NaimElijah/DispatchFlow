package com.dispatchflow.messaging.domain.rating;

import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.STOP;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.on;
import static com.dispatchflow.messaging.domain.MessagingFixtures.uuid;
import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.messaging.domain.RatingQuestion;
import com.dispatchflow.messaging.domain.rating.Answer.Counted;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The test builder of {@link RatingRequest}: {@code aRatingRequest()} is a saved, open request whose
 * question went to {@code MOBILE} and was accepted at 10:00, with no reply yet. Each step is a reply
 * or a retention run, with messages on, so every state is one the commands reach (ADR-127 decision
 * 2). {@link #build} replays the steps on a new request each time and saves it, so the events the
 * steps raised are gone and one builder may build many.
 */
final class RatingRequestBuilder {

    static final RatingRequestId REQUEST = RatingRequestId.of(uuid(500));

    /** The version {@link #build} leaves: a request's first save stores 0 (ADR-126). */
    static final Version LOADED = Version.of(0);

    private final List<Consumer<RatingRequest>> steps = new ArrayList<>();

    private RatingRequestBuilder() {}

    static RatingRequestBuilder aRatingRequest() {
        return new RatingRequestBuilder();
    }

    /** A bare rating, which also sends the thank-you when it is the first. */
    RatingRequestBuilder rated(int value, Instant at) {
        steps.add(request -> request.answer(Reply.parse(Integer.toString(value)), on(), at));
        return this;
    }

    /**
     * A reply kept as a comment alone. Before a rating, the first one also sends the format
     * reminder (FR-359).
     */
    RatingRequestBuilder withComment(String text, Instant at) {
        steps.add(request -> {
            if (request.answer(Reply.parse(text), on(), at).counted() != Counted.COMMENT) {
                throw new AssertionError("a builder comment is kept as a comment alone");
            }
        });
        return this;
    }

    /** The retention job's run at {@code now}, which removes whatever personal data is due. */
    RatingRequestBuilder personalDataRemoved(Instant now) {
        steps.add(request -> request.removePersonalData(now));
        return this;
    }

    /** A new request that ran every step, saved as a use case would have saved it. */
    RatingRequest build() {
        RatingRequest request = RatingRequest.open(REQUEST, new RatingQuestion(STOP, MOBILE, TEN));
        steps.forEach(step -> step.accept(request));
        return asSaved(request);
    }
}
