package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.orders.application.spi.OrderPlacement;
import com.dispatchflow.orders.application.spi.OrderPlacementFact;
import com.dispatchflow.orders.application.spi.PlacementFact;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planning's implementation of Customers &amp; Orders' {@link OrderPlacement} (DL §2.1's dashed arrow,
 * DL §9.6, BR-33): it reads where the plans of a date hold its orders through {@link PlanningViews}.
 * An abandoned or cancelled plan's orders count as unplanned (BR-131, FR-337), which the answer's
 * standing tells.
 *
 * <p>{@link #placementOf} reads only the plans of the date the caller names, so an order's old date
 * never answers for it after a reschedule.
 *
 * <p>To Be Implemented. ADR-121 settles it.
 */
@Transactional(readOnly = true)
public class OrderPlacementService implements OrderPlacement {

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
     * <p>To Be Implemented.
     */
    @Override
    public PlacementFact placementOf(OrderId order, DeliveryDate date, Attempt attempt) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public List<OrderPlacementFact> placementsOn(DeliveryDate date) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
