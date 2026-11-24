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
 * <p>To Be Implemented.
 */
public interface PublishedRoutes {

    /**
     * Every route of a date's plan in force. Live Operations picks a driver's own routes from it
     * (FR-27).
     *
     * <p>To Be Implemented.
     *
     * @param date the date
     * @return the routes, in the plan's order; empty when the date has no plan in force
     */
    List<PublishedRouteFact> inForceOn(DeliveryDate date);

    /**
     * Every route of every plan in force from a date on, so a driver's later routes are listed with
     * their dates (FR-324). Live Operations picks a driver's own routes from it.
     *
     * <p>To Be Implemented.
     *
     * @param from the first date, usually today
     * @return the routes, by date and then in each plan's order; empty when no plan in force is that
     *     late
     */
    List<PublishedRouteFact> inForceFrom(DeliveryDate from);

    /**
     * One route of a plan in force.
     *
     * <p>To Be Implemented.
     *
     * @param route the route
     * @return the route, or empty when no plan in force holds it now
     */
    Optional<PublishedRouteFact> route(RouteId route);

    /**
     * The route of a plan in force that holds a stop now. Live Operations projects one stop's
     * arrival, Live Operations checks a driver's access to a proof (BR-02), and Recipient Messaging
     * explains why a stop gets no message (FR-378), from it.
     *
     * <p>To Be Implemented. It serves Live Operations' {@code ProofQueries}.
     *
     * @param stop the stop
     * @return the route, or empty when no plan in force holds the stop now
     */
    Optional<PublishedRouteFact> routeOf(StopId stop);

    /**
     * What one revision did to the plan's stops: which joined a route, moved, or left (ADR-059).
     *
     * <p>To Be Implemented.
     *
     * @param plan the plan
     * @param revision the revision's number, 1 or more
     * @return the revision's stop changes
     * @throws NotFound when the plan has no such revision
     */
    RevisionChangesFact changesIn(PlanId plan, int revision);
}
