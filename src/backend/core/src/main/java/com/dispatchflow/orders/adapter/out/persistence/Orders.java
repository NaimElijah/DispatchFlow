package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.Order;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaOrderRepository}. */
interface Orders extends Repository<Order, UUID> {

    /**
     * Saves an order and publishes its pending events.
     *
     * @param order the order
     * @return the managed order
     */
    Order save(Order order);

    /**
     * Loads an order, which has no collection, so its row is all of it.
     *
     * @param id the order's UUID
     * @return the order, or empty
     */
    Optional<Order> findById(UUID id);

    /**
     * Takes a date's capacity lock for the caller's transaction, waiting for it (BR-101, BR-113,
     * ADR-127 decision 9). The date is bound as its ISO text, so the key doesn't depend on the
     * session's date style.
     *
     * @param date the date, as {@code yyyy-MM-dd}
     * @return 1, always: the call is made for its lock
     */
    @Query(
            value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext('orders:date-capacity:' || :date))",
            nativeQuery = true)
    int lockDate(String date);

    /**
     * A date's uncancelled orders, failed ones included, as their keys and references.
     *
     * @param date the date
     * @return the orders, in no order
     */
    @Query("""
            select new com.dispatchflow.orders.adapter.out.persistence.DatedOrderRow(o.id, o.reference)
            from Order o where o.date = :date and o.state = ACTIVE
            """)
    List<DatedOrderRow> uncancelledRowsOn(DeliveryDate date);

    /**
     * A date's uncancelled orders, whole.
     *
     * @param date the date
     * @return the orders, in no order
     */
    @Query("select o from Order o where o.date = :date and o.state = ACTIVE")
    List<Order> uncancelledOn(DeliveryDate date);

    /**
     * The orders not yet anonymized dated on or before a date.
     *
     * @param lastDate the latest date included
     * @return the orders' UUIDs, in no order
     */
    @Query("select o.id from Order o where o.anonymized = false and o.date <= :lastDate")
    List<UUID> notAnonymizedUpTo(DeliveryDate lastDate);

    /**
     * A customer's orders not yet anonymized, cancelled ones and every date included.
     *
     * @param customer the customer
     * @return the orders' UUIDs, in no order
     */
    @Query("select o.id from Order o where o.customer = :customer and o.anonymized = false")
    List<UUID> notAnonymizedOf(CustomerId customer);
}
