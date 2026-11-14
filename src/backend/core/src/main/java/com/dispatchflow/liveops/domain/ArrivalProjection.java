package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.StopId;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * When each stop of a route is now expected to be reached (DL §10.4, ADR-067, FR-254, FR-383),
 * computed when asked and never stored. It propagates what has happened along the stored legs and
 * never re-applies a factor:
 *
 * <ol>
 *   <li>The route's latest event in time sets where the vehicle leaves from: the start (it leaves
 *       at the start time), a stop with an outcome (it leaves at the outcome's time), or a stop
 *       only arrived at (it leaves at the later of arrival plus service and now). With no event,
 *       it leaves at the planned departure.
 *   <li>Each later stop in planned order is reached at the later of the travel and now, so no
 *       arrival is projected before now (FR-254). A hard window absorbs delay by waiting; a door
 *       stop never waits (BR-122).
 *   <li>A later stop that already has a fact is passed on its leg with no service, and gets no
 *       projection. A stop before the latest event in planned order with no fact, skipped
 *       (BR-29), has no leg from where the vehicle is, so it gets none either (ADR-107).
 * </ol>
 *
 * <p>A stop is at risk when its projected arrival is later than its window's end less 15 minutes,
 * and a door stop never is (FR-253, FR-369).
 */
public final class ArrivalProjection {

    /** How close to its window's end a stop's projected arrival may come before it is at risk (FR-253). */
    public static final Duration AT_RISK_MARGIN = Duration.ofMinutes(15);

    private final Map<StopId, Instant> arrivals;
    private final Map<StopId, PlannedStopLeg> legs;

    private ArrivalProjection(Map<StopId, Instant> arrivals, Map<StopId, PlannedStopLeg> legs) {
        this.arrivals = arrivals;
        this.legs = legs;
    }

    /**
     * Projects a route's arrivals.
     *
     * @param planned the current revision's stored legs
     * @param facts what each reported stop's delivery shows; a stop with no fact may be left out
     * @param routeStart when the route run started, if it has
     * @param now the time to project at
     * @return the projection
     */
    public static ArrivalProjection of(
            PlannedLegs planned, List<StopFact> facts, Optional<Instant> routeStart, Instant now) {
        Objects.requireNonNull(planned, "planned");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(routeStart, "routeStart");
        Objects.requireNonNull(now, "now");
        Map<StopId, StopFact> reported = new LinkedHashMap<>();
        facts.stream().filter(StopFact::isReported).forEach(fact -> reported.put(fact.stop(), fact));
        List<PlannedStopLeg> stops = planned.stops();

        int anchor = -1;
        Instant leaves = routeStart.orElse(planned.plannedDeparture().toInstant());
        Optional<Instant> latest = routeStart;
        for (int i = 0; i < stops.size(); i++) {
            StopFact fact = reported.get(stops.get(i).stop());
            if (fact == null) {
                continue;
            }
            Instant at = fact.latest().orElseThrow();
            if (latest.isEmpty() || !at.isBefore(latest.get())) {
                Duration service = stops.get(i).service();
                latest = Optional.of(at);
                anchor = i;
                leaves = fact.outcomeAt()
                        .orElseGet(() -> later(fact.arrivedAt().orElseThrow().plus(service), now));
            }
        }

        Map<StopId, Instant> arrivals = new LinkedHashMap<>();
        Map<StopId, PlannedStopLeg> legs = new LinkedHashMap<>();
        Instant t = leaves;
        for (int j = anchor + 1; j < stops.size(); j++) {
            PlannedStopLeg leg = stops.get(j);
            legs.put(leg.stop(), leg);
            t = t.plus(leg.legFromPrevious());
            if (reported.containsKey(leg.stop())) {
                continue;
            }
            Instant arrival = later(t, now);
            arrivals.put(leg.stop(), arrival);
            Instant start =
                    leg.door() ? arrival : later(arrival, leg.window().start().toInstant());
            t = start.plus(leg.service());
        }
        return new ArrivalProjection(arrivals, legs);
    }

    /**
     * A stop's projected arrival.
     *
     * @param stop the stop
     * @return the arrival, or empty for a stop with a fact, a skipped one, or one not on the route
     */
    public Optional<Instant> arrivalOf(StopId stop) {
        return Optional.ofNullable(arrivals.get(Objects.requireNonNull(stop, "stop")));
    }

    /**
     * Whether a stop is at risk of missing its window (FR-253, FR-369).
     *
     * @param stop the stop
     * @return {@code true} when it has a projection later than its window's end less 15 minutes and
     *     is not a door stop
     */
    public boolean isAtRisk(StopId stop) {
        Optional<Instant> arrival = arrivalOf(stop);
        if (arrival.isEmpty()) {
            return false;
        }
        PlannedStopLeg leg = legs.get(stop);
        return !leg.door()
                && arrival.get().isAfter(leg.window().end().toInstant().minus(AT_RISK_MARGIN));
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
