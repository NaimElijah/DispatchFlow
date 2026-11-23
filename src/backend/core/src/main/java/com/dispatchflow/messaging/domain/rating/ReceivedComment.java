package com.dispatchflow.messaging.domain.rating;

import com.dispatchflow.shared.PersonalData;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

/**
 * A comment on a delivery, with when its reply was received (FR-360). Admins and Dispatchers read
 * it; it is deleted with the rating request's other personal data (NFR-184, NFR-193). It is stored
 * in its request's comment table.
 *
 * @param comment the reply, cut to 500 characters (NFR-203)
 * @param receivedAt when the reply was received
 */
@Embeddable
public record ReceivedComment(Comment comment, Instant receivedAt) implements PersonalData {

    /** Checks every part is present. */
    public ReceivedComment {
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /**
     * A form that hides the comment.
     *
     * @return the time, without the comment
     */
    @Override
    public String toString() {
        return "ReceivedComment[comment=redacted, receivedAt=" + receivedAt + "]";
    }
}
