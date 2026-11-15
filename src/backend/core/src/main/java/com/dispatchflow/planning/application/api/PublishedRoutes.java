package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.NotFound;
import java.util.List;
import java.util.Optional;

/**
 * The routes of the plans in force, as their current revisions have them (DL §2.2, ADR-059,
 * ADR-067, ADR-101): what Live Operations projects, tracks, alerts on and shows each driver. A plan
 * is in force once published, until it is cancelled; a completed plan still is, so late reports
 * and corrections find its routes.
 *
 * <p>Built in M5.
 */
public interface PublishedRoutes {

    /**
     * Every route of a date's plan in force. Live Operations picks a driver's own routes from it
     * (FR-27).
     *
     * <p>Built in M5.
     *
     * @param date the date
     * @return the routes, in the plan's order; empty when the date has no plan in force
     */
    List<PublishedRouteFact> inForceOn(DeliveryDate date);

    /**
     * One route of a plan in force.
     *
     * <p>Built in M5.
     *
     * @param route the route
     * @return the route, or empty when no plan in force holds it now
     */
    Optional<PublishedRouteFact> route(RouteId route);

    /**
     * The route of a plan in force that holds a stop now. Live Operations projects one stop's
     * arrival, and Recipient Messaging explains why a stop gets no message (FR-378), from it.
     *
     * <p>Built in M8.
     *
     * @param stop the stop
     * @return the route, or empty when no plan in force holds the stop now
     */
    Optional<PublishedRouteFact> routeOf(StopId stop);

    /**
     * What one revision did to the plan's stops: which joined a route, moved, or left (ADR-059).
     *
     * <p>Built in M12.
     *
     * @param plan the plan
     * @param revision the revision's number, 1 or more
     * @return the revision's stop changes
     * @throws NotFound when the plan has no such revision
     */
    RevisionChangesFact changesIn(PlanId plan, int revision);
}
