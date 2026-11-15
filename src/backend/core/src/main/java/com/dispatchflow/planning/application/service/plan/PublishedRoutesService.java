package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.planning.application.api.PublishedRouteFact;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import java.util.List;
import java.util.Optional;

/**
 * Planning's implementation of {@link PublishedRoutes}: it reads the current revisions of the plans in
 * force through {@link PlanningViews} (ADR-059, ADR-067, ADR-101).
 *
 * <p>Built in M5.
 */
public final class PublishedRoutesService implements PublishedRoutes {

    private final PlanningViews views;

    /**
     * Creates the service.
     *
     * @param views Planning's read side, with the stored revisions
     */
    public PublishedRoutesService(PlanningViews views) {
        this.views = views;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M5.
     */
    @Override
    public List<PublishedRouteFact> inForceOn(DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M5.
     */
    @Override
    public Optional<PublishedRouteFact> route(RouteId route) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
