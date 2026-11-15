package com.dispatchflow.planning.application.spi;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;

/**
 * What has happened on a date's routes, which Planning asks before it cancels a plan, accepts a
 * proposal or re-optimizes (DL §2.3 rule 2, FR-336, BR-25, BR-82, ADR-050, ADR-101). Live
 * Operations implements it as its {@code ExecutionFactsService} (DL §2.1's dashed arrow).
 *
 * <p>Built in M9.
 */
public interface ExecutionFacts {

    /**
     * The started routes and the reported stops of a date, which cancelling its plan judges
     * (FR-336).
     *
     * <p>Built in M9.
     *
     * @param date the date
     * @return the started routes and the reported stops
     */
    StartedRoutesFact startedOn(DeliveryDate date);

    /**
     * The stops of a date with an accepted outcome or an arrival recorded since an instant, which
     * Planning's {@code ChangesSinceBasis.reportedStops} holds. A report held for the dispatcher
     * doesn't count until it is confirmed (BR-132, ADR-101).
     *
     * <p>Built in M9.
     *
     * @param date the date
     * @param since the instant the reports count from, excluded, by when the server recorded them
     * @return the reported stops, each once
     */
    List<StopId> changesSince(DeliveryDate date, Instant since);

    /**
     * What has happened on a date's routes up to now, for a re-optimization (ADR-050 points 1 to
     * 3).
     *
     * <p>Built in M9.
     *
     * @param date the date
     * @param now the re-optimization's {@code now}; nothing recorded after it counts
     * @return the reported stops with their corrected times, the finished routes and each route's
     *     last two positions
     */
    LiveStateFact liveStateOn(DeliveryDate date, Instant now);
}
