package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StopId;
import java.util.Objects;

/**
 * The stop a put-back gave an order after a failed stop, which a correction to delivered is
 * checked against (FR-258, FR-404, ADR-107). Live Operations checks this stop's outcome in its own
 * {@code Delivery}, and builds its {@code LaterAttempt} from both.
 *
 * <p>Built in M8.
 *
 * @param stop the put-back's stop
 * @param attempt its attempt
 * @param date the delivery date it is on, whose plan holds it
 */
public record LaterStopFact(StopId stop, Attempt attempt, DeliveryDate date) {

    /** Checks every part is present. */
    public LaterStopFact {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(date, "date");
    }
}
