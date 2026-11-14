package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TimeWindow;
import java.time.Duration;
import java.util.Objects;

/**
 * One stop of the current revision's route as the optimizer timed it, in Live Operations' own terms
 * (DL §2.3 rule 1, §10.4): the buffered travel from the place before it, its service time, its
 * window, and whether it is a door stop, whose window does not bind (BR-119, BR-122).
 *
 * @param stop the stop
 * @param legFromPrevious the buffered travel from the depot or the stop before it
 * @param service the service time at the stop
 * @param window the stop's window
 * @param door whether it is a leave-at-door stop
 */
public record PlannedStopLeg(StopId stop, Duration legFromPrevious, Duration service, TimeWindow window, boolean door) {

    /**
     * Checks the parts.
     *
     * @throws StoredStateInvalid when a duration is negative, which only damaged state from Planning
     *     can give
     */
    public PlannedStopLeg {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(legFromPrevious, "legFromPrevious");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(window, "window");
        if (legFromPrevious.isNegative() || service.isNegative()) {
            throw new StoredStateInvalid(PlannedStopLeg.class, "a leg and a service time are never negative");
        }
    }
}
