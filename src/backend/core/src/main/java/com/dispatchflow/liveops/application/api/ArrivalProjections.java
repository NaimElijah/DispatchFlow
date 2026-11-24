package com.dispatchflow.liveops.application.api;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * When stops are now expected to be reached, propagated along the stored legs when asked (DL
 * §10.4, ADR-067, ADR-114), and how far a route and a stop have come. Messaging's minute job asks
 * it instead of hearing an event, because projections change as time passes (DL §7.4).
 *
 * <p>To Be Implemented.
 */
public interface ArrivalProjections {

    /**
     * Every stop of a date's routes in force, with its projection.
     *
     * <p>To Be Implemented.
     *
     * @param date the date
     * @param now the time to project at
     * @return one answer per stop, in route and plan order; empty when the date has no plan in
     *     force
     */
    List<StopArrivalFact> projectedOn(DeliveryDate date, Instant now);

    /**
     * One stop's projection, if a route in force holds it now. A stop the plan took off since the
     * caller learned of it has none, which is an expected answer, not an error.
     *
     * <p>To Be Implemented.
     *
     * @param stop the stop
     * @param now the time to project at
     * @return the stop's answer, or empty when no route in force holds the stop now
     */
    Optional<StopArrivalFact> of(StopId stop, Instant now);

    /**
     * How far a route and one of its stops have come, from Live Operations' own {@code RouteRun}
     * and {@code Delivery}, whatever the plan holds now (FR-354, FR-373). Messaging asks it for a
     * stop the plan may already have taken off. It projects nothing, so it needs no route in force.
     *
     * <p>To Be Implemented.
     *
     * @param route the route that held the stop
     * @param stop the stop
     * @return the route's progress, {@code NOT_STARTED} when it has no run, and the stop's, {@code
     *     NOT_REACHED} when it has no delivery
     */
    ProgressFact progressOf(RouteId route, StopId stop);
}
