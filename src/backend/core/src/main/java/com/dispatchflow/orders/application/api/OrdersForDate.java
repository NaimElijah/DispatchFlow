package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.DeliveryDate;
import java.time.Instant;
import java.util.List;

/**
 * A date's orders, as Planning and Live Operations read them (DL §2.2, BR-22). Each answer is read
 * now; the caller translates it into its own values (DL §2.3 rule 1).
 *
 * <p>Built in M2 and M9, method by method.
 */
public interface OrdersForDate {

    /**
     * Every uncancelled order of a date: what a solve and a publication read, and Live Operations'
     * live map for locations and last changes (FR-15, FR-397, BR-22, BR-71). Each order's {@code
     * placedAt} has no source yet: M2 adds the time the order joined its date, set by {@code
     * place}, {@code reschedule} and a put-back to another date (BR-71).
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @return the orders, each once; empty for a date with none
     */
    List<OrderFact> uncancelledOn(DeliveryDate date);

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
