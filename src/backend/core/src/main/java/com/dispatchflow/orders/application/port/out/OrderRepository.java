package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.DateCapacity;
import com.dispatchflow.orders.domain.Order;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Where orders are kept, as whole {@link Order} aggregates (DL §3.6, §4.3), with the date's
 * capacity read under its lock and the lookups the use cases need. The events an order raised
 * leave with its save, in the same transaction (DL §3.6). It has no query for screens: {@link
 * OrdersViews} serves those.
 *
 * <p>Built in M2, and in an unscheduled milestone for the retention and copy lookups.
 */
public interface OrderRepository {

    /**
     * Loads an order, whatever its state.
     *
     * <p>Built in M2.
     *
     * @param id the order's ID
     * @return the order at its stored version, or empty when none has the ID
     */
    Optional<Order> get(OrderId id);

    /**
     * Stores a new order, in the caller's transaction.
     *
     * <p>Built in M2.
     *
     * @param order the order, at {@code Version.NEW}
     */
    void add(Order order);

    /**
     * Stores a changed order and publishes the events it raised, in the caller's transaction, if
     * its stored version is still the one it was loaded at (BR-76, ADR-057, DL §3.6).
     *
     * <p>Built in M2.
     *
     * @param order the order
     * @throws StaleVersion when another change was saved since it was loaded
     */
    void save(Order order);

    /**
     * A date's uncancelled orders, read after taking the date's transaction-scoped advisory lock,
     * which the caller's transaction holds until it ends (BR-101, BR-113, NFR-05, ADR-060, DL §5).
     * Every use case that adds an order to a date reads it, so two of them can't both take the
     * date's last place or one reference.
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @return the date's capacity, failed orders included (BR-113)
     */
    DateCapacity capacityUnderLock(DeliveryDate date);

    /**
     * The orders whose personal fields are due for removal on a day: dated at least 90 days before
     * it and not yet anonymized (NFR-257, ADR-075). {@link Order#anonymize} decides each one.
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param today today in {@code Asia/Jerusalem}
     * @return the orders to anonymize, each once
     */
    List<OrderId> dueForAnonymization(LocalDate today);

    /**
     * A date's uncancelled orders, whole, which a copy to another date places again (FR-62).
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param date the date copied from
     * @return the orders, each once; empty for a date with none
     */
    List<Order> uncancelledOn(DeliveryDate date);

    /**
     * Every order of a customer not yet anonymized, cancelled ones and every date included, which
     * retiring the customer asks Planning about. Planning owns whether a plan is completed, so this
     * filters by no date (BR-43, DL §4.3).
     *
     * <p>Built in M2.
     *
     * @param customer the customer
     * @return the orders' IDs, each once; empty when the customer has none
     */
    List<OrderId> notAnonymizedOf(CustomerId customer);
}
