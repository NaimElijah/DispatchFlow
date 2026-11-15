package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.application.service.DeviceClock;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A driver starts their route (FR-68, FR-244, FR-327). Outside calls first: the start's {@code
 * CorrectedTime} from the {@link DeviceClock} and the server's {@link Clock} (BR-114), Planning's
 * {@link RouteMembership#driversAt} for the route's driver at that time and now (ADR-114), and
 * {@link PublishedRoutes} for the route's date. Then one transaction creates the {@code RouteRun}
 * through {@code RouteRun.start}, which raises {@code RouteStarted}, with the audit record; a route
 * that already has a run is refused by the run itself.
 *
 * <p>Built in M5. Rows: FR-68, FR-244, FR-327.
 */
public final class StartRoute {

    private final RouteRunRepository runs;
    private final RouteMembership membership;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param runs where route runs are kept
     * @param membership Planning's answers on a route's drivers at a time
     * @param publishedRoutes Planning's routes in force, for the route's date
     * @param clock the server's time
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public StartRoute(
            RouteRunRepository runs,
            RouteMembership membership,
            PublishedRoutes publishedRoutes,
            Clock clock,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.runs = runs;
        this.membership = membership;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Starts the route.
     *
     * <p>Built in M5.
     *
     * @param actor the driver
     * @param route the route
     * @param time the device's time of the start and its clock when it sent it
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     DeviceTimeOutOfRange}; {@code NotYourRoute} when the driver is not both the route's driver
     *     at that time and now (BR-02, ADR-114); else {@code RouteAlreadyFinished} or {@code
     *     RunAlreadyStarted}
     * @throws NotFound when no plan in force holds the route
     * @throws StaleVersion when another start or first report created the run meanwhile
     */
    public void start(Actor actor, RouteId route, DeviceClock time) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
