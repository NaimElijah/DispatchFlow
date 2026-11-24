package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.planning.application.api.PublishedRouteFact;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RevisionChangesFact;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planning's implementation of {@link PublishedRoutes}: it reads the current revisions of the plans in
 * force through {@link PlanningViews} (ADR-059, ADR-067, ADR-101).
 *
 * <p>It fills each {@code PublishedStopFact}'s window, service time and door flag from the revision,
 * as the revision took its order, never from the order as it is now, so a stop whose order changed
 * since keeps the leg it was planned with. The revision stores the three once they are added to
 * its stops (FR-364, BR-49, BR-124).
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class PublishedRoutesService implements PublishedRoutes {

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
     * <p>To Be Implemented.
     */
    @Override
    public List<PublishedRouteFact> inForceOn(DeliveryDate date) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public List<PublishedRouteFact> inForceFrom(DeliveryDate from) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public Optional<PublishedRouteFact> route(RouteId route) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public Optional<PublishedRouteFact> routeOf(StopId stop) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public RevisionChangesFact changesIn(PlanId plan, int revision) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
