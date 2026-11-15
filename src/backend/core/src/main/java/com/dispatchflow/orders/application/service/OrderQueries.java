package com.dispatchflow.orders.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.orders.application.port.out.DateOrdersView;
import com.dispatchflow.orders.application.port.out.OrderView;
import com.dispatchflow.orders.application.port.out.OrdersViews;
import com.dispatchflow.orders.application.spi.OrderPlacement;
import com.dispatchflow.orders.application.spi.StopProgress;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.application.NotFound;

/**
 * The order screens' reads (FR-76, BR-33): a date's orders and one order, each with its status. The
 * status is derived, never stored (DL §9.6): for a date, the queries read the orders, Planning's
 * {@link OrderPlacement} and Live Operations' {@link StopProgress} once each, translate the
 * answers, and call {@code OrderStatus.derive} per order, so 200 orders cost three reads; for one
 * order, they ask {@link OrderPlacement#placementOf} with its date and current attempt. Every
 * read is the dispatcher's, authorized by role from its operation (ADR-068); no driver scope
 * applies.
 *
 * <p>The ETA FR-76 asks for is Live Operations' projection, which Customers &amp; Orders can't read
 * (DL §2.1), so the screen takes it from Live Operations.
 *
 * <p>Built in M2.
 */
public final class OrderQueries {

    private final OrdersViews views;
    private final OrderPlacement orderPlacement;
    private final StopProgress stopProgress;

    /**
     * Creates the queries.
     *
     * @param views Customers &amp; Orders' read port
     * @param orderPlacement where the plans hold the orders, as Planning answers
     * @param stopProgress what happened at the orders' stops, as Live Operations answers
     */
    public OrderQueries(OrdersViews views, OrderPlacement orderPlacement, StopProgress stopProgress) {
        this.views = views;
        this.orderPlacement = orderPlacement;
        this.stopProgress = stopProgress;
    }

    /**
     * Every order of a date with its status (FR-76, BR-33). Live Operations' {@link StopProgress}
     * answers no report for every stop in M2, and in full from M5 (ADR-121), so until M5 no order is
     * delivered or failed.
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return the date's orders, cancelled ones included; empty for a date with none
     */
    public DateOrdersView ofDate(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * One order with its status (FR-57, BR-33).
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param order the order
     * @return the order
     * @throws NotFound when no order has the ID
     */
    public OrderView show(Actor actor, OrderId order) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
