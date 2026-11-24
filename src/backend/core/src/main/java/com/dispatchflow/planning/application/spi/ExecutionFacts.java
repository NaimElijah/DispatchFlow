package com.dispatchflow.planning.application.spi;

import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;

/**
 * What has happened on a plan's routes and stops, among those given, which Planning asks before it
 * cancels a plan, accepts a proposal or re-optimizes (DL §2.3 rule 2, FR-336, BR-25, BR-82,
 * ADR-050, ADR-101). Live Operations implements it as its {@code ExecutionFactsService} (DL §2.1's
 * dashed arrow).
 *
 * <p>To Be Implemented.
 */
public interface ExecutionFacts {

    /**
     * Which of a plan's routes have started and which of its stops are reported, which cancelling
     * the plan judges (FR-336). Planning names its own routes and stops, so Live Operations reads
     * only its own records and never asks Planning back.
     *
     * <p>To Be Implemented.
     *
     * @param routes the plan's routes
     * @param stops the plan's stops
     * @return the started routes and the reported stops, among those given
     */
    StartedRoutesFact startedAmong(List<RouteId> routes, List<StopId> stops);

    /**
     * The stops among a plan's with an accepted outcome or an arrival recorded since an instant, which
     * Planning's {@code ChangesSinceBasis.reportedStops} holds. A report held for the dispatcher
     * doesn't count until it is confirmed (BR-132, ADR-101).
     *
     * <p>To Be Implemented.
     *
     * @param stops the plan's stops
     * @param since the instant the reports count from, excluded, by when the server recorded them
     * @return the reported stops among those given, each once
     */
    List<StopId> changesSince(List<StopId> stops, Instant since);

    /**
     * What has happened on a plan's routes up to now, for a re-optimization (ADR-050 points 1 to
     * 3).
     *
     * <p>To Be Implemented.
     *
     * @param routes the plan's routes
     * @param stops the plan's stops
     * @param now the re-optimization's {@code now}; nothing recorded after it counts
     * @return the reported stops with their corrected times, the finished routes and each route's
     *     last two positions
     */
    LiveStateFact liveStateOf(List<RouteId> routes, List<StopId> stops, Instant now);
}
