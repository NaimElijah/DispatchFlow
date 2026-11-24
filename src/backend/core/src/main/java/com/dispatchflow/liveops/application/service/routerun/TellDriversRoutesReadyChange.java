package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.events.PlanPublished;
import com.dispatchflow.shared.AccountId;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only transaction of {@link TellDriversRoutesReady} (FR-152, FR-324): it reads the driver
 * of each of the plan's routes from Planning's {@link PublishedRoutes}, so the notices go out after
 * it.
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class TellDriversRoutesReadyChange {

    private final PublishedRoutes publishedRoutes;

    /**
     * Creates the read.
     *
     * @param publishedRoutes Planning's routes in force, for each route's driver
     */
    public TellDriversRoutesReadyChange(PublishedRoutes publishedRoutes) {
        this.publishedRoutes = publishedRoutes;
    }

    /**
     * The drivers with a route on the published plan.
     *
     * <p>To Be Implemented.
     *
     * @param event the published plan
     * @return each driver once, however many of the plan's routes they drive
     */
    public List<AccountId> driversOf(PlanPublished event) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
