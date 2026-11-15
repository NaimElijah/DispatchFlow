package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A driver confirms they have seen a change to their route: <em>Seen</em> (FR-97, FR-250). Planning's
 * {@link PublishedRoutes} gives the route's current driver and the plan's current revision, and one
 * transaction calls {@code RouteRun.acknowledge} with the audit record. The confirmed revision never
 * goes back, and a route with no run stores nothing (ADR-107).
 *
 * <p>Built in M8. Rows: FR-97, FR-250.
 */
public final class AcknowledgeRouteChange {

    private final RouteRunRepository runs;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param runs where route runs are kept
     * @param publishedRoutes Planning's routes in force, for the current driver and revision
     * @param clock the server's time, for the audit record
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public AcknowledgeRouteChange(
            RouteRunRepository runs,
            PublishedRoutes publishedRoutes,
            Clock clock,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.runs = runs;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Records the revision the driver saw.
     *
     * <p>Built in M8.
     *
     * @param actor the driver
     * @param route the route
     * @param seen the revision the phone showed
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code NotYourRoute} when
     *     the driver no longer drives the route (ADR-114), else {@code UnknownRevision} when {@code
     *     seen} is past the plan's current revision (ADR-107)
     * @throws NotFound when no plan in force holds the route
     * @throws StaleVersion when another writer saved the run meanwhile
     */
    public void acknowledge(Actor actor, RouteId route, RevisionNo seen) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
