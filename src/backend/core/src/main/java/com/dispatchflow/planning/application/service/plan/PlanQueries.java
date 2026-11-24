package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.port.out.DatePlansView;
import com.dispatchflow.planning.application.port.out.PlanHistoryView;
import com.dispatchflow.planning.application.port.out.PlanWorkspaceView;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.planning.application.port.out.plan.PreflightView;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.NotFound;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dispatcher's planning screens: a date's plans, a plan's workspace on the map, the timeline and
 * the table, the pre-flight check before a solve, and a plan's history (FR-18, FR-24, FR-25, FR-104,
 * FR-141, FR-143, FR-144, FR-148 to FR-151, FR-223, FR-230 to FR-232, FR-240, FR-241, FR-385).
 *
 * <p>These are dispatcher screens: the Driver role never reaches a plan that is draft, solving, ready
 * or abandoned, whatever the account's other roles (BR-41). The handler's role check from the
 * operation's {@code x-capability} keeps drivers out (ADR-068), and this class enforces BR-41 again
 * once it is built.
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class PlanQueries {

    private final PlanningViews views;
    private final FleetFacts fleetFacts;
    private final OrdersForDate ordersForDate;

    /**
     * Creates the queries.
     *
     * @param views Planning's read side
     * @param fleetFacts Fleet's register and roster, for the pre-flight check
     * @param ordersForDate the date's orders, for the pre-flight check
     */
    public PlanQueries(PlanningViews views, FleetFacts fleetFacts, OrdersForDate ordersForDate) {
        this.views = views;
        this.fleetFacts = fleetFacts;
        this.ordersForDate = ordersForDate;
    }

    /**
     * A date's plans, so the dispatcher opens the date's plan or creates one (FR-15, BR-34).
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return the date's plans, possibly none
     */
    public DatePlansView ofDate(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * A plan's workspace: its routes, stops, drops with their reasons, violations, pins and journal
     * (FR-18, FR-24, FR-25, FR-143, FR-144, FR-148 to FR-151, FR-223, FR-232, FR-241, FR-385).
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @return the workspace
     * @throws NotFound when no plan has the ID
     */
    public PlanWorkspaceView workspace(Actor actor, PlanId plan) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * The pre-flight check before a solve, read from the plan's date's orders and roster now (FR-141,
     * FR-230, FR-231).
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan about to be solved
     * @return the known causes of drops or a failed solve; a clean check promises nothing (FR-231)
     * @throws NotFound when no plan has the ID
     */
    public PreflightView preflight(Actor actor, PlanId plan) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * A plan's revisions: who made each, when, why, and which routes it changed (FR-104).
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @return the history
     * @throws NotFound when no plan has the ID
     */
    public PlanHistoryView history(Actor actor, PlanId plan) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
