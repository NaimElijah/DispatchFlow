package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import java.time.Instant;
import java.util.List;

/**
 * A date's orders, as Planning, Live Operations and Reporting read them (DL §2.2, BR-22, ADR-109).
 * Each answer is read now; the caller translates it into its own values (DL §2.3 rule 1).
 *
 * <p>Built in M2, M5 and M9, method by method.
 */
public interface OrdersForDate {

    /**
     * Every uncancelled order of a date: what a solve, a publication and Reporting's on-time rates
     * read (FR-15, BR-22, BR-71, FR-264).
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @return the orders, each once; empty for a date with none
     */
    List<OrderFact> uncancelledOn(DeliveryDate date);

    /**
     * The orders of a date that are cancelled now, which Reporting's {@code CancelledOrders} holds
     * and neither on-time rate counts (BR-30, ADR-109).
     *
     * <p>Built in M5.
     *
     * @param date the date
     * @return the cancelled orders, each once
     */
    List<OrderId> cancelledOn(DeliveryDate date);

    /**
     * What changed among a date's orders since an instant (BR-25, BR-82, ADR-056).
     *
     * <p>Built in M9.
     *
     * @param date the date
     * @param since the instant the changes count from, excluded
     * @return the cancelled and the changed orders
     */
    OrderChanges changesSince(DeliveryDate date, Instant since);
}
