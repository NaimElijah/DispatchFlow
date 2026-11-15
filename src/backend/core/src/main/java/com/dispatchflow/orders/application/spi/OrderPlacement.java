package com.dispatchflow.orders.application.spi;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import java.util.List;

/**
 * Where plans hold orders, which Customers &amp; Orders asks to derive each order's status (BR-33,
 * DL §9.6) and before it puts an order back (FR-75, FR-258). Planning implements it as its {@code
 * OrderPlacementService} (DL §2.1's dashed arrow).
 *
 * <p>Built in M2, for the order screens and a change's answer (ADR-121).
 */
public interface OrderPlacement {

    /**
     * Where the plan of a date holds one attempt of an order. Planning keeps no order's date, so
     * the caller names it, and Planning reads that date's plans only. It reads the same Planning
     * view as {@link #placementsOn}, so the order screens and a change's answer agree (FR-396,
     * ADR-121).
     *
     * <p>Built in M2.
     *
     * @param order the order
     * @param date the date whose plans are asked, the order's date
     * @param attempt the attempt asked about
     * @return the placement, or {@link PlacementFact.NotPlaced} when no plan of the date holds it
     */
    PlacementFact placementOf(OrderId order, DeliveryDate date, Attempt attempt);

    /**
     * Where the plans of a date hold its orders, for the order list's statuses (FR-76, BR-33).
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @return one entry per order a plan of that date holds; an order left out is not placed
     */
    List<OrderPlacementFact> placementsOn(DeliveryDate date);
}
