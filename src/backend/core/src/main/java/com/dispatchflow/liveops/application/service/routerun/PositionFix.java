package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.liveops.application.service.DeviceClock;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * One position a phone or the web app sends in a batch while a route is in progress (FR-31, FR-243).
 * Its time is corrected for the device's clock like a report's (BR-114). It is a person's location,
 * so it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param at where the device was
 * @param time when the device recorded it, and the device's clock when it sent the batch
 */
public record PositionFix(GeoPoint at, DeviceClock time) implements PersonalData {

    /** Checks both parts are present. */
    public PositionFix {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(time, "time");
    }

    /**
     * A form that hides the location.
     *
     * @return {@code PositionFix[redacted]}
     */
    @Override
    public String toString() {
        return "PositionFix[redacted]";
    }
}
