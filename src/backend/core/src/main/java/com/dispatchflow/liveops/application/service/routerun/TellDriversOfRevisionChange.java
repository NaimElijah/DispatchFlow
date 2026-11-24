package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only transaction of {@link TellDriversOfRevision} (FR-71, FR-249): it reads each changed
 * route's current driver from Planning's {@link PublishedRoutes}, so the notices go out after it.
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class TellDriversOfRevisionChange {

    private final PublishedRoutes publishedRoutes;

    /**
     * Creates the read.
     *
     * @param publishedRoutes Planning's routes in force, for each changed route's driver
     */
    public TellDriversOfRevisionChange(PublishedRoutes publishedRoutes) {
        this.publishedRoutes = publishedRoutes;
    }

    /**
     * The driver of each route the revision changed.
     *
     * <p>To Be Implemented.
     *
     * @param event the revision
     * @return each changed route with its current driver; none for a route the revision left with
     *     no driver
     */
    public Map<RouteId, AccountId> driversOf(PlanRevised event) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
