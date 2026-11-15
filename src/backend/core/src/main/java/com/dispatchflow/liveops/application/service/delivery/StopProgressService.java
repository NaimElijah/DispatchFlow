package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.orders.application.spi.OrderStopProgress;
import com.dispatchflow.orders.application.spi.StopProgress;
import com.dispatchflow.orders.application.spi.StopProgressFact;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import java.util.List;

/**
 * Live Operations' implementation of Customers &amp; Orders' {@link StopProgress} spi (DL §2.1's
 * dashed arrow, DL §2.3 rule 2): what has happened at orders' stops, which an order's change,
 * reschedule or cancel is judged by (BR-92) and its status derived from (BR-33, DL §9.6). It reads
 * the attempt's delivery: the effective outcome counts, after any correction (FR-333), and a held
 * report is not an outcome, though an arrival recorded before it counts (BR-132). A date's answer
 * takes the date's stops from Planning's {@link PublishedRoutes}.
 *
 * <p>M2's order screens and order changes need an answer before Live Operations records any report,
 * so M2 builds it answering <em>no report</em> for every stop: {@link StopProgressFact.NoFact} for
 * one attempt, and no entry for a date's orders. M5 builds it in full, from the deliveries (ADR-121).
 *
 * <p>Built in M2, answering no report for every stop; in full in M5.
 */
public final class StopProgressService implements StopProgress {

    private final DeliveryRepository deliveries;
    private final PublishedRoutes publishedRoutes;

    /**
     * Creates the service.
     *
     * @param deliveries where deliveries are kept
     * @param publishedRoutes Planning's routes in force, for a date's stops
     */
    public StopProgressService(DeliveryRepository deliveries, PublishedRoutes publishedRoutes) {
        this.deliveries = deliveries;
        this.publishedRoutes = publishedRoutes;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2, answering {@link StopProgressFact.NoFact}; in full in M5.
     */
    @Override
    public StopProgressFact stateOf(OrderId order, Attempt attempt) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2, answering no entry; in full in M5.
     */
    @Override
    public List<OrderStopProgress> statesOn(DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
