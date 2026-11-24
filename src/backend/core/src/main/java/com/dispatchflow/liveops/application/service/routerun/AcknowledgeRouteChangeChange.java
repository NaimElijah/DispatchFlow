package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link AcknowledgeRouteChange} (FR-97, FR-250): it reads the route's current
 * driver and the plan's current revision from Planning's {@link PublishedRoutes}, and calls {@code
 * RouteRun.acknowledge} with the audit record (ADR-107, NFR-23).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class AcknowledgeRouteChangeChange {

    private final RouteRunRepository runs;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param runs where route runs are kept
     * @param publishedRoutes Planning's routes in force, for the current driver and revision
     * @param clock the server's time, for the audit record
     * @param auditLog the audit trail
     */
    public AcknowledgeRouteChangeChange(
            RouteRunRepository runs, PublishedRoutes publishedRoutes, Clock clock, AuditLog auditLog) {
        this.runs = runs;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Records the revision the driver saw and writes the audit record.
     *
     * <p>To Be Implemented.
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
    public void apply(Actor actor, RouteId route, RevisionNo seen) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
