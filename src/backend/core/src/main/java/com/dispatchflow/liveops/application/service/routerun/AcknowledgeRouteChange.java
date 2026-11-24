package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A driver confirms they have seen a change to their route: <em>Seen</em> (FR-97, FR-250). Planning's
 * {@link PublishedRoutes} gives the route's current driver and the plan's current revision, and
 * {@link AcknowledgeRouteChangeChange} holds the one transaction, which calls {@code
 * RouteRun.acknowledge} with the audit record. The confirmed revision never goes back, and a route
 * with no run stores nothing (ADR-107). Like every changing call it writes its audit record, a
 * refused attempt's in its own transaction (NFR-23, ADR-110). After the commit, it tells the open
 * screens through {@link LiveUpdates}.
 *
 * <p>Built in M8. Rows: FR-97, FR-250.
 */
public final class AcknowledgeRouteChange {

    private final LiveUpdates liveUpdates;
    private final AcknowledgeRouteChangeChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the open screens
     * @param change the use case's transaction
     */
    public AcknowledgeRouteChange(LiveUpdates liveUpdates, AcknowledgeRouteChangeChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
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
     * @throws StaleVersion when another writer saved the run meanwhile; the web adapter retries the
     *     call at once, up to three times, then answers 503 with {@code Retry-After} (ADR-121)
     */
    public void acknowledge(Actor actor, RouteId route, RevisionNo seen) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
