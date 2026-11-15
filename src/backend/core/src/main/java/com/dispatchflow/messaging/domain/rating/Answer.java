package com.dispatchflow.messaging.domain.rating;

import com.dispatchflow.messaging.domain.ReplyMessage;
import java.util.Objects;
import java.util.Optional;

/**
 * What a reply counted for, and which answer is due (FR-357 to FR-360, FR-379).
 *
 * @param counted what the reply counted for
 * @param due the message to send back, if one is due and may go
 */
public record Answer(Counted counted, Optional<ReplyMessage> due) {

    /** What a reply counted for. */
    public enum Counted {
        /** The delivery's rating (FR-357). */
        RATING,
        /** The delivery's rating, and the reply kept as a comment (FR-357, FR-360). */
        RATING_AND_COMMENT,
        /** A comment only (FR-360). */
        COMMENT,
        /** Nothing: an opt-out word, a blank reply, or a bare rating after the first (FR-357). */
        NOTHING,
        /** Not an answer to this request: received outside its 48 hours, so not kept (FR-379). */
        DISCARDED
    }

    /** Checks every part is present. */
    public Answer {
        Objects.requireNonNull(counted, "counted");
        Objects.requireNonNull(due, "due");
    }
}
