package com.dispatchflow.planning.application.spi;

import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import java.time.Instant;
import java.util.Objects;

/**
 * One position a route's driver sent, as a re-optimization places the vehicle by it (ADR-050 point
 * 2, BR-79, BR-80). It is a person's location, so it prints redacted (NFR-30).
 *
 * <p>To Be Implemented.
 *
 * @param at where the device was
 * @param recordedAt when the device recorded it, corrected for its clock (BR-114)
 * @param receivedAt when the server received it
 */
public record PositionFact(GeoPoint at, Instant recordedAt, Instant receivedAt) implements PersonalData {

    /** Checks every part is present. */
    public PositionFact {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /**
     * A form that hides the location.
     *
     * @return {@code PositionFact[redacted]}
     */
    @Override
    public String toString() {
        return "PositionFact[redacted]";
    }
}
