package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.rating.Rating;
import com.dispatchflow.messaging.domain.rating.RatingRequest;
import com.dispatchflow.messaging.domain.rating.ReceivedComment;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.StopId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A date's ratings and comments, which Admins and Dispatchers read per delivery (FR-360). It names
 * no route and no driver, so it gives no per-route or per-driver figure until Q-42 is answered
 * (FR-381). Comments are a person's words, so it prints redacted.
 *
 * <p>Built in M12.
 *
 * @param date the date
 * @param stops the date's stops that had a rating request, in route and plan order
 */
public record RatingsView(DeliveryDate date, List<RatedStop> stops) implements PersonalData {

    /** Checks the date is present, and copies the stops. */
    public RatingsView {
        Objects.requireNonNull(date, "date");
        stops = List.copyOf(stops);
    }

    /**
     * A form that leaves the comments out.
     *
     * @return the date and the number of stops
     */
    @Override
    public String toString() {
        return "RatingsView[date=" + date + ", stops=" + stops.size() + ", redacted]";
    }

    /**
     * One stop's rating request.
     *
     * <p>Built in M12.
     *
     * @param stop the stop
     * @param request its rating request
     * @param status open, rated or closed when read
     * @param rating the rating that counted, or empty when none did (FR-357)
     * @param comments the replies kept as comments, oldest first, each until its retention ends
     *     (FR-360, NFR-193)
     */
    public record RatedStop(
            StopId stop,
            RatingRequestId request,
            RatingRequest.Status status,
            Optional<Rating> rating,
            List<ReceivedComment> comments)
            implements PersonalData {

        /** Checks every part is present, possibly empty, and copies the comments. */
        public RatedStop {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(rating, "rating");
            comments = List.copyOf(comments);
        }

        /**
         * A form that leaves the comments out.
         *
         * @return the stop and the status, redacted
         */
        @Override
        public String toString() {
            return "RatedStop[stop=" + stop + ", status=" + status + ", redacted]";
        }
    }
}
