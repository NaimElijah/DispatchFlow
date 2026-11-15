package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.orders.application.spi.OrderPlacement;
import com.dispatchflow.orders.application.spi.OrderPlacementFact;
import com.dispatchflow.orders.application.spi.PlacementFact;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import java.util.List;

/**
 * Planning's implementation of Customers &amp; Orders' {@link OrderPlacement} (DL §2.1's dashed arrow,
 * DL §9.6, BR-33): it reads where the plans of a date hold its orders through {@link PlanningViews}.
 * An abandoned or cancelled plan's orders count as unplanned (BR-131, FR-337), which the answer's
 * standing tells.
 *
 * <p>Built in M2 and M9, method by method.
 */
public final class OrderPlacementService implements OrderPlacement {

    private final PlanningViews views;

    /**
     * Creates the service.
     *
     * @param views Planning's read side
     */
    public OrderPlacementService(PlanningViews views) {
        this.views = views;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M9.
     */
    @Override
    public PlacementFact placementOf(OrderId order, Attempt attempt) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2.
     */
    @Override
    public List<OrderPlacementFact> placementsOn(DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
