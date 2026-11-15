package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.fleet.application.spi.PlanUseFact;
import com.dispatchflow.orders.application.spi.OrderPlacementFact;
import com.dispatchflow.orders.application.spi.PlacementFact;
import com.dispatchflow.planning.application.api.BaselineInputsFact;
import com.dispatchflow.planning.application.api.LaterStopFact;
import com.dispatchflow.planning.application.api.MembershipFact;
import com.dispatchflow.planning.application.api.PlanStandingFact;
import com.dispatchflow.planning.application.api.PlannedFiguresFact;
import com.dispatchflow.planning.application.api.PublishedRouteFact;
import com.dispatchflow.planning.application.api.RouteDriversFact;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Planning's read side: what its screens show, and what other modules read about its plans,
 * straight from Planning's own schema without loading an aggregate (CQRS-lite, TS §2.1 rule 5, DL
 * §3.6). The persistence adapter implements it. It never reads another module's schema (DL §2.3 rule
 * 3), so a screen that needs another module's data joins it in the query class or in the client.
 *
 * <p>The facts other modules read come back as Planning's {@code api} records, or as the records of
 * the {@code spi} Planning implements, so the services that implement them translate nothing twice.
 *
 * <p>Built in M0 to M10, method by method.
 */
public interface PlanningViews {

    /**
     * The plans of a date (FR-15, BR-34).
     *
     * <p>Built in M0.
     *
     * @param date the date
     * @return the date's plans, possibly none
     */
    DatePlansView plansOn(DeliveryDate date);

    /**
     * One plan's workspace (FR-24, FR-25, FR-143, FR-151, FR-223, FR-232).
     *
     * <p>Built in M0.
     *
     * @param plan the plan
     * @return the workspace, or empty when no plan has the ID
     */
    Optional<PlanWorkspaceView> workspace(PlanId plan);

    /**
     * One plan's revisions (FR-104).
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param plan the plan
     * @return the history, or empty when no plan has the ID
     */
    Optional<PlanHistoryView> history(PlanId plan);

    /**
     * One solve job's progress (FR-142).
     *
     * <p>Built in M4.
     *
     * @param job the job
     * @param asOf the server's time of the answer
     * @return the progress, or empty when no job has the ID
     */
    Optional<SolveProgressView> progress(JobId job, Instant asOf);

    /**
     * One route as the plan holds it now (FR-24, FR-72, FR-153).
     *
     * <p>Built in M3.
     *
     * @param route the route
     * @return the route, or empty when no plan holds it
     */
    Optional<RouteView> route(RouteId route);

    /**
     * What changed on a route since a revision (FR-249).
     *
     * <p>Built in M10.
     *
     * @param route the route
     * @param since the revision compared from, 1 or more
     * @return the changes, or empty when no published plan holds the route at that revision
     */
    Optional<RouteChangesView> routeChanges(RouteId route, int since);

    /**
     * One re-optimization's preview (FR-36, FR-103, FR-334, BR-81).
     *
     * <p>Built in M9.
     *
     * @param proposal the proposal
     * @return the preview, or empty when no proposal has the ID
     */
    Optional<ProposalPreviewView> proposal(ProposalId proposal);

    /**
     * The planning settings, or their starting values when an Admin never changed any (FR-183).
     *
     * <p>Built in M4.
     *
     * @return the settings
     */
    PlanningSettingsView settings();

    /**
     * Every route of a date's plan in force, from its current revision (ADR-059, ADR-067).
     *
     * <p>Built in M5.
     *
     * @param date the date
     * @return the routes, in the plan's order; empty when the date has no plan in force
     */
    List<PublishedRouteFact> routesInForceOn(DeliveryDate date);

    /**
     * One route of a plan in force, from its current revision.
     *
     * <p>Built in M5.
     *
     * @param route the route
     * @return the route, or empty when no plan in force holds it now
     */
    Optional<PublishedRouteFact> routeInForce(RouteId route);

    /**
     * Whether a stop was on a route at a time, from the revision in force then, and where it is now
     * (BR-02, BR-27, BR-110, BR-132, ADR-114).
     *
     * <p>Built in M5.
     *
     * @param stop the stop
     * @param route the route
     * @param at the corrected time (BR-114)
     * @return the membership, or empty when no plan holds or held the stop
     */
    Optional<MembershipFact> membershipAt(StopId stop, RouteId route, Instant at);

    /**
     * A route's driver in the revision in force at a time, and its driver now (FR-68, FR-329,
     * ADR-114).
     *
     * <p>Built in M5.
     *
     * @param route the route
     * @param at the corrected time (BR-114)
     * @return the drivers, or empty when no revision in force at that time holds the route
     */
    Optional<RouteDriversFact> driversAt(RouteId route, Instant at);

    /**
     * The stop a put-back gave an order after an attempt (FR-258, FR-404, ADR-107).
     *
     * <p>Built in M8.
     *
     * @param order the order
     * @param attempt the earlier attempt
     * @return the next attempt's stop, or empty when no plan holds one
     */
    Optional<LaterStopFact> laterStopOf(OrderId order, Attempt attempt);

    /**
     * A published plan's planned figures, from its current revision (FR-38, FR-39, FR-266).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return the figures, or empty when no published plan has the ID
     */
    Optional<PlannedFiguresFact> figuresOf(PlanId plan);

    /**
     * Where a published plan is now (FR-338, ADR-109).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return its standing, or empty when no published plan has the ID
     */
    Optional<PlanStandingFact> standingOf(PlanId plan);

    /**
     * What a published plan's daily solve was given, for its baseline (FR-39, FR-342, ADR-052).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return the inputs, or empty when no published plan has the ID
     */
    Optional<BaselineInputsFact> inputsOf(PlanId plan);

    /**
     * The plan that holds a stop, or held it in any revision, so a late outcome finds its plan after
     * the stop left it (DL §8).
     *
     * <p>Built in M8.
     *
     * @param stop the stop
     * @return the plan, or empty when no plan ever held the stop
     */
    Optional<PlanId> planOfStop(StopId stop);

    /**
     * The plan that holds a route; a route never leaves its plan (ADR-101).
     *
     * <p>Built in M5.
     *
     * @param route the route
     * @return the plan, or empty when no plan holds the route
     */
    Optional<PlanId> planOfRoute(RouteId route);

    /**
     * Where the plan of an order's date holds one attempt (BR-33, DL §9.6).
     *
     * <p>Built in M9.
     *
     * @param order the order
     * @param attempt the attempt
     * @return the placement, or {@link PlacementFact.NotPlaced} when no plan of the date holds it
     */
    PlacementFact placementOf(OrderId order, Attempt attempt);

    /**
     * Where the plans of a date hold its orders (FR-76, BR-33).
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @return one entry per order a plan of that date holds
     */
    List<OrderPlacementFact> placementsOn(DeliveryDate date);

    /**
     * The plans not yet completed that serve or drop an order of a customer (BR-43). Planning keeps
     * no customer of its own, so this needs each stop's customer from the solve's input, which
     * Customers &amp; Orders' {@code OrderFact} doesn't carry yet.
     *
     * <p>Built in M2.
     *
     * @param customer the customer
     * @return the plans, each once
     */
    List<PlanId> unfinishedPlansUsing(CustomerId customer);

    /**
     * The plans dated on or after a day whose routes use a vehicle (FR-305).
     *
     * <p>Built in M2.
     *
     * @param vehicle the vehicle
     * @param from the first date that counts
     * @return the plans, cancelled ones included
     */
    List<PlanUseFact> plansUsingVehicle(VehicleId vehicle, LocalDate from);

    /**
     * The plans dated on or after a day whose routes start or end at a depot (FR-305).
     *
     * <p>Built in M2.
     *
     * @param depot the depot
     * @param from the first date that counts
     * @return the plans, cancelled ones included
     */
    List<PlanUseFact> plansUsingDepot(DepotId depot, LocalDate from);

    /**
     * The plans dated on or after a day whose routes a driver drives (FR-305).
     *
     * <p>Built in M2.
     *
     * @param driver the driver's account
     * @param from the first date that counts
     * @return the plans, cancelled ones included
     */
    List<PlanUseFact> plansUsingDriver(AccountId driver, LocalDate from);
}
