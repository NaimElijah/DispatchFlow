package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.Objects;

/**
 * One position a route kept, after {@code RouteRun.acceptsPositionAt} said yes (DL §4.5, FR-31,
 * FR-328, NFR-30): a record in {@link PositionLog}, deleted 7 days after it was received (NFR-31).
 * It is a person's location, so it prints redacted.
 *
 * @param route the route it was sent for
 * @param driver the driver who sent it
 * @param at where the device was
 * @param recordedAt when the device recorded it, corrected for the device's clock (BR-114)
 * @param receivedAt when the server received it
 */
public record KeptPosition(RouteId route, AccountId driver, GeoPoint at, Instant recordedAt, Instant receivedAt)
        implements PersonalData {

    /** Checks every part is present. */
    public KeptPosition {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /**
     * A form that hides the location.
     *
     * @return {@code KeptPosition[redacted]}
     */
    @Override
    public String toString() {
        return "KeptPosition[redacted]";
    }
}
