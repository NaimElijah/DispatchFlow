package com.dispatchflow.orders.application.spi;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import java.util.List;

/**
 * What has happened at orders' stops, which Customers &amp; Orders asks before it changes,
 * reschedules or cancels an order (BR-92), and to derive each order's status (BR-33, DL §9.6).
 * Live Operations implements it as its {@code StopProgressService} (DL §2.1's dashed arrow).
 *
 * <p>Built in M2, answering no fact for every stop; in full in M5 (ADR-121).
 */
public interface StopProgress {

    /**
     * What has happened at one attempt's stop.
     *
     * <p>Built in M2, answering no fact for every stop; in full in M5 (ADR-121).
     *
     * @param order the order
     * @param attempt the attempt whose stop is asked about, usually the order's current one
     * @return the stop's fact, or {@link StopProgressFact.NoFact} when the attempt has no stop or
     *     nothing is recorded there
     */
    StopProgressFact stateOf(OrderId order, Attempt attempt);

    /**
     * What has happened at the stops of a date, for the order list's statuses (FR-76, BR-33).
     *
     * <p>Built in M2, answering no fact for every stop; in full in M5 (ADR-121).
     *
     * @param date the date
     * @return one entry per order with a stop on that date's plan; an order left out has no fact
     */
    List<OrderStopProgress> statesOn(DeliveryDate date);
}
