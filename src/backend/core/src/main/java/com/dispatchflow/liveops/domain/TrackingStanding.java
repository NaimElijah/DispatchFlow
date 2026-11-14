package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.IsraelDateTime;
import java.util.Objects;

/**
 * Whether a position's sender may still be tracked on a route, and until when, as Planning and Fleet
 * answer at the time of the check (NFR-30, FR-328, FR-394): after a hand-over the old driver's
 * positions are refused, so are those of a driver marked unavailable, and the current driver's
 * shift end, or the confirmed return time, sets the cut-off (ADR-107).
 *
 * @param currentDriver whether the sender drives the route now (Planning)
 * @param available whether the driver is available today (Fleet)
 * @param shiftEnd the current driver's shift end that day, or the confirmed return time of FR-394
 *     (Fleet, Planning)
 */
public record TrackingStanding(boolean currentDriver, boolean available, IsraelDateTime shiftEnd) {

    /** Checks the shift end is present. */
    public TrackingStanding {
        Objects.requireNonNull(shiftEnd, "shiftEnd");
    }
}
