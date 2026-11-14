package com.dispatchflow.messaging.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.Objects;

/**
 * A recipient rated a delivery (FR-357, DL §7.2). Messaging's own listener raises a Live
 * Operations alert for a rating of 1 to 4, and the alert must follow reliably (FR-361; DL §7.1,
 * reason 2). It carries the rating's number, never the phone or a comment.
 *
 * @param eventId the event's ID
 * @param occurredAt when the rating reply was received
 * @param request the rating request the reply answered
 * @param stop the stop whose delivery was rated
 * @param rating the rating, 1 to 10
 */
public record RatingReceived(EventId eventId, Instant occurredAt, RatingRequestId request, StopId stop, int rating)
        implements DomainEvent {

    /** Checks every field is present. */
    public RatingReceived {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(stop, "stop");
    }
}
