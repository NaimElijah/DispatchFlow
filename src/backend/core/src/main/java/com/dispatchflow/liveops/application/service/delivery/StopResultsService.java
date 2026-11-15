package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.liveops.application.api.StopResultFact;
import com.dispatchflow.liveops.application.api.StopResults;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.PlanId;
import java.util.List;

/**
 * Live Operations' implementation of {@link StopResults}, which Reporting reads when a figure is
 * asked for (FR-38, FR-333, ADR-109). It takes each stop's arrival and effective outcome from its
 * delivery, a held report counting as no outcome (BR-132), and translates them into {@link
 * StopResultFact}s.
 *
 * <p>Planning's API answers a plan's routes by date, not by plan, so {@link #of(PlanId)} waits for a
 * lookup from a plan to its stops.
 *
 * <p>Built in M5.
 */
public final class StopResultsService implements StopResults {

    private final PublishedRoutes publishedRoutes;
    private final DeliveryRepository deliveries;

    /**
     * Creates the service.
     *
     * @param publishedRoutes Planning's routes, for a plan's stops
     * @param deliveries where deliveries are kept
     */
    public StopResultsService(PublishedRoutes publishedRoutes, DeliveryRepository deliveries) {
        this.publishedRoutes = publishedRoutes;
        this.deliveries = deliveries;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M5.
     */
    @Override
    public List<StopResultFact> of(PlanId plan) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
