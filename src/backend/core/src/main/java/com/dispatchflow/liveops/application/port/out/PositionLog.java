package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.List;

/**
 * The positions routes kept: records appended and never changed, deleted 7 days after receipt (DL
 * §3.2, §4.5, FR-31, NFR-31). It is the one high-volume stream, so it has no version (DL §11).
 *
 * <p>Built in M8 and M13, method by method.
 */
public interface PositionLog {

    /**
     * Appends a batch's kept positions, in the caller's transaction.
     *
     * <p>Built in M8.
     *
     * @param positions the positions, possibly none
     */
    void append(List<KeptPosition> positions);

    /**
     * A route's last two kept positions, which place its vehicle for a re-optimization (ADR-050
     * point 2, BR-79, BR-80).
     *
     * <p>Built in M8.
     *
     * @param route the route
     * @return the positions, the latest by recorded time last; fewer than two when fewer were kept
     */
    List<KeptPosition> lastTwo(RouteId route);

    /**
     * Deletes every position received before a cutoff (NFR-31, ADR-075).
     *
     * <p>Built in M13.
     *
     * @param cutoff the cutoff, from {@code RetentionPeriods.positionCutoff}
     * @return how many positions were deleted
     */
    int deleteOlderThan(Instant cutoff);
}
