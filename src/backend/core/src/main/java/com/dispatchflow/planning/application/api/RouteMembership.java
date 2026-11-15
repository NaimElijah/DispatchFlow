package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.NotFound;
import java.time.Instant;
import java.util.Optional;

/**
 * Planning's answers on which route held a stop when, read from the stored revisions (DL §2.2, DL
 * §8, ADR-059, ADR-114). Live Operations judges a report, starts and finishes a route run, and
 * checks a correction after a put-back by them.
 *
 * <p>Built in M5, M8 and M12, method by method.
 */
public interface RouteMembership {

    /**
     * Whether a stop was on a report's route at the report's corrected time, who drove the route
     * then and now, and where the stop is now (BR-02, BR-27, BR-110, BR-132, ADR-114).
     *
     * <p>Built in M5.
     *
     * @param stop the reported stop
     * @param route the route the report came from
     * @param correctedTime the report's corrected time (BR-114)
     * @return the entitlement and the stop's whereabouts
     * @throws NotFound when no plan holds the stop
     */
    MembershipFact membershipAt(StopId stop, RouteId route, Instant correctedTime);

    /**
     * A route's driver at a corrected time and its driver now, for a route's start or finish,
     * which name no stop (FR-68, FR-329, ADR-114).
     *
     * <p>Built in M5.
     *
     * @param route the route
     * @param correctedTime the report's corrected time (BR-114)
     * @return the drivers, or empty when no revision in force at that time holds the route
     * @throws NotFound when no plan holds the route
     */
    Optional<RouteDriversFact> driversAt(RouteId route, Instant correctedTime);

    /**
     * The stop a put-back gave the order after an attempt, if it was put back (FR-258, FR-404,
     * ADR-107).
     *
     * <p>Built in M8.
     *
     * @param order the order
     * @param attempt the attempt whose stop is being corrected
     * @return the next attempt's stop, or empty when the order was not put back after it
     */
    Optional<LaterStopFact> laterStopOf(OrderId order, Attempt attempt);

    /**
     * The stop that served an order's attempt on a date, and the route that last held it, even
     * after Planning took the stop off (ADR-059). Recipient Messaging tells a recipient their
     * delivery changed from it (FR-354).
     *
     * <p>Built in M12.
     *
     * @param order the order
     * @param attempt the attempt
     * @param date the date whose plan held it
     * @return the stop and its route, or empty when no revision of that date's plan held the attempt
     */
    Optional<OrderStopFact> stopOf(OrderId order, Attempt attempt, DeliveryDate date);
}
