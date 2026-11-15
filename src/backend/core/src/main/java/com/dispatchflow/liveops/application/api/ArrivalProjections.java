package com.dispatchflow.liveops.application.api;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.NotFound;
import java.time.Instant;
import java.util.List;

/**
 * When stops are now expected to be reached, propagated along the stored legs when asked (DL
 * §10.4, ADR-067, ADR-114). Messaging's minute job asks it instead of hearing an event, because
 * projections change as time passes (DL §7.4).
 *
 * <p>Built in M8.
 */
public interface ArrivalProjections {

    /**
     * Every stop of a date's routes in force, with its projection.
     *
     * <p>Built in M8.
     *
     * @param date the date
     * @param now the time to project at
     * @return one answer per stop, in route and plan order; empty when the date has no plan in
     *     force
     */
    List<StopArrivalFact> projectedOn(DeliveryDate date, Instant now);

    /**
     * One stop's projection.
     *
     * <p>Built in M8.
     *
     * @param stop the stop
     * @param now the time to project at
     * @return the stop's answer
     * @throws NotFound when no route in force holds the stop
     */
    StopArrivalFact of(StopId stop, Instant now);
}
