package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.planning.application.api.LaterStopFact;
import com.dispatchflow.planning.application.api.MembershipFact;
import com.dispatchflow.planning.application.api.RouteDriversFact;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.Optional;

/**
 * Planning's implementation of {@link RouteMembership}: it reads the stored revisions through {@link
 * PlanningViews}, never by loading a plan, because a plan doesn't load its past revisions (DL §4.4,
 * ADR-059, ADR-114).
 *
 * <p>Built in M5 and M8, method by method.
 */
public final class RouteMembershipService implements RouteMembership {

    private final PlanningViews views;

    /**
     * Creates the service.
     *
     * @param views Planning's read side, with the stored revisions
     */
    public RouteMembershipService(PlanningViews views) {
        this.views = views;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M5.
     */
    @Override
    public MembershipFact membershipAt(StopId stop, RouteId route, Instant correctedTime) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M5.
     */
    @Override
    public Optional<RouteDriversFact> driversAt(RouteId route, Instant correctedTime) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M8.
     */
    @Override
    public Optional<LaterStopFact> laterStopOf(OrderId order, Attempt attempt) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
