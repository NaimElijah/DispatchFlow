package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.shared.StopId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
 *       projection.
 *   <li>A stop before the latest event in planned order with no fact, skipped (BR-29), has no leg
 *       from where the vehicle is. It gets its planned arrival, timed from the planned departure
 *       along the legs as if nothing had happened, and never earlier than now (FR-254), so its
 *       recipient still hears when to expect it in "out for delivery" (FR-351, ADR-114). It is not
 *       carried forward ({@link #isCarriedForward}), so it never sends "arriving soon" or "time
 *       changed".
 * </ol>
 *
 * <p>A stop is at risk when its projected arrival is later than its window's end less 15 minutes,
 * and a door stop never is (FR-253, FR-369). A skipped stop is never at risk on its planned arrival
 * alone, because nothing observed carries delay to it (ADR-114).
 */
public final class ArrivalProjection {

    /** How close to its window's end a stop's projected arrival may come before it is at risk (FR-253). */
    public static final Duration AT_RISK_MARGIN = Duration.ofMinutes(15);

    private final Map<StopId, Instant> arrivals;
    private final Map<StopId, PlannedStopLeg> propagated;

    private ArrivalProjection(Map<StopId, Instant> arrivals, Map<StopId, PlannedStopLeg> propagated) {
        this.arrivals = arrivals;
        this.propagated = propagated;
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
        List<Instant> asPlanned = plannedArrivals(planned);
        for (int j = 0; j < anchor; j++) {
            StopId skipped = stops.get(j).stop();
            if (!reported.containsKey(skipped)) {
                arrivals.put(skipped, later(asPlanned.get(j), now));
            }
        }
        Map<StopId, PlannedStopLeg> propagated = new LinkedHashMap<>();
        Instant t = leaves;
        for (int j = anchor + 1; j < stops.size(); j++) {
            PlannedStopLeg leg = stops.get(j);
            t = t.plus(leg.legFromPrevious());
            if (reported.containsKey(leg.stop())) {
                continue;
            }
            Instant arrival = later(t, now);
            arrivals.put(leg.stop(), arrival);
            propagated.put(leg.stop(), leg);
            t = serviceEnds(leg, arrival);
        }
        return new ArrivalProjection(arrivals, propagated);
    }

    /**
     * A stop's projected arrival.
     *
     * @param stop the stop
     * @return the arrival, or empty for a stop with a fact or one not on the route
     */
    public Optional<Instant> arrivalOf(StopId stop) {
        return Optional.ofNullable(arrivals.get(Objects.requireNonNull(stop, "stop")));
    }

    /**
     * Whether a stop's arrival is carried forward along the legs from the route's start or its
     * latest event, rather than a skipped stop's planned arrival (ADR-114). Messaging sends
     * "arriving soon" and "time changed" only on a carried-forward arrival, because a planned one
     * held at now moves with the clock alone (FR-352, FR-353).
     *
     * @param stop the stop
     * @return {@code true} for a projection carried forward; {@code false} for a skipped stop's
     *     planned arrival, a stop with a fact, or one not on the route
     */
    public boolean isCarriedForward(StopId stop) {
        return propagated.containsKey(Objects.requireNonNull(stop, "stop"));
    }

    /**
     * Whether a stop is at risk of missing its window (FR-253, FR-369).
     *
     * @param stop the stop
     * @return {@code true} when its projection, carried from the latest event, is later than its
     *     window's end less 15 minutes and it is not a door stop; a skipped stop never is
     */
    public boolean isAtRisk(StopId stop) {
        PlannedStopLeg leg = propagated.get(Objects.requireNonNull(stop, "stop"));
        if (leg == null) {
            return false;
        }
        return !leg.door()
                && arrivals.get(stop).isAfter(leg.window().end().toInstant().minus(AT_RISK_MARGIN));
    }

    /** Each stop's arrival as planned: from the planned departure along the legs, with no fact. */
    private static List<Instant> plannedArrivals(PlannedLegs planned) {
        List<Instant> arrivals = new ArrayList<>();
        Instant t = planned.plannedDeparture().toInstant();
        for (PlannedStopLeg leg : planned.stops()) {
            Instant arrival = t.plus(leg.legFromPrevious());
            arrivals.add(arrival);
            t = serviceEnds(leg, arrival);
        }
        return arrivals;
    }

    /** When service ends at a stop reached at {@code arrival}: a hard window waits, a door stop doesn't. */
    private static Instant serviceEnds(PlannedStopLeg leg, Instant arrival) {
        Instant start =
                leg.door() ? arrival : later(arrival, leg.window().start().toInstant());
        return start.plus(leg.service());
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
