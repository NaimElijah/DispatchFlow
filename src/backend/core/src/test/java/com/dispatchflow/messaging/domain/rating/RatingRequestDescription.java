package com.dispatchflow.messaging.domain.rating;

import com.dispatchflow.messaging.domain.rating.RatingRequest.OneTimeReply;
import com.dispatchflow.messaging.domain.rating.RatingRequest.Rated;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What a test sees of a {@link RatingRequest}, read through its getters: a value that compares by
 * every part. Tests compare a request before and after a reply, or with the state they expect,
 * through it.
 *
 * @param id the request's ID
 * @param stop the delivered stop
 * @param phone the phone the question went to, until removed
 * @param acceptedAt when the question was accepted
 * @param rating the rating, once given
 * @param comments the comments kept
 * @param reminder what became of the format reminder
 * @param thankYou what became of the thank-you
 * @param removedAt when the phone was removed, if it was
 */
public record RatingRequestDescription(
        RatingRequestId id,
        StopId stop,
        Optional<PhoneNumber> phone,
        Instant acceptedAt,
        Optional<Rated> rating,
        List<ReceivedComment> comments,
        OneTimeReply reminder,
        OneTimeReply thankYou,
        Optional<Instant> removedAt) {

    /**
     * Describes a request.
     *
     * @param request the request
     * @return what its getters give
     */
    public static RatingRequestDescription of(RatingRequest request) {
        return new RatingRequestDescription(
                request.id(),
                request.stop(),
                request.phone(),
                request.acceptedAt(),
                request.rating(),
                request.comments(),
                request.reminder(),
                request.thankYou(),
                request.removedAt());
    }
}
