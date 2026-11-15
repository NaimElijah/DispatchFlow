package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.Objects;

/**
 * A "delivered" message that asks for a rating was accepted (DL §6.2): the use case opens a {@link
 * com.dispatchflow.messaging.domain.rating.RatingRequest} from it in the same transaction.
 *
 * @param stop the delivered stop
 * @param phone the phone the question went to
 * @param acceptedAt when the provider accepted it, from which the 48 hours run (FR-357)
 */
public record RatingQuestion(StopId stop, PhoneNumber phone, Instant acceptedAt) implements PersonalData {

    /** Checks every part is present. */
    public RatingQuestion {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
    }

    /**
     * A form that hides the phone.
     *
     * @return the question without its phone
     */
    @Override
    public String toString() {
        return "RatingQuestion[stop=" + stop + ", phone=redacted, acceptedAt=" + acceptedAt + "]";
    }
}
