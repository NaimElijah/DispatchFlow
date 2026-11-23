package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.Customer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaCustomerRepository}. */
interface Customers extends Repository<Customer, UUID> {

    /**
     * Saves a customer.
     *
     * @param customer the customer
     * @return the managed customer
     */
    Customer save(Customer customer);

    /**
     * Loads a customer with its address flags.
     *
     * @param id the customer's UUID
     * @return the customer, or empty
     */
    @EntityGraph(attributePaths = "flags")
    Optional<Customer> findById(UUID id);

    /**
     * The active customers whose stored normalized address is the one given, with their flags.
     *
     * @param normalizedAddress the normalized address's text
     * @return the customers, in no order
     */
    @EntityGraph(attributePaths = "flags")
    @Query("select c from Customer c where c.normalizedAddress = :normalizedAddress and c.state = ACTIVE")
    List<Customer> activeAt(String normalizedAddress);

    /**
     * The latest delivery date of any of a customer's orders, cancelled ones included.
     *
     * @param customer the customer's UUID
     * @return the date, or empty when the customer has no order
     */
    @Query(value = "SELECT max(date) FROM orders.customer_order WHERE customer = :customer", nativeQuery = true)
    Optional<LocalDate> lastDeliveryDateOf(UUID customer);

    /**
     * The customers whose retention may be over, as {@code Customer.anonymize} counts it in Israel's
     * calendar: a retired customer once its retirement plus the retention period has passed, and an
     * active one once the later of its creation's day and its last order's date, plus the period, is
     * not after today. PostgreSQL resolves a retirement's local time as {@code Customer.anonymize}
     * does, except that a local time the autumn clock change repeats is taken in standard time: an
     * hour later, never earlier, so the customer's own check never refuses a customer this returns.
     *
     * @param now when the retention job runs
     * @param zone Israel's zone ID
     * @param retention the retention period, in ISO 8601 form
     * @return the customers' UUIDs, in no order
     */
    @Query(value = """
            SELECT c.id FROM orders.customer c
            WHERE (c.state = 'RETIRED'
                   AND (c.state_since AT TIME ZONE :zone + CAST(:retention AS interval)) AT TIME ZONE :zone <= :now)
               OR (c.state = 'ACTIVE'
                   AND CAST(c.created_at AT TIME ZONE :zone AS date) + CAST(:retention AS interval)
                       <= CAST(CAST(:now AS timestamptz) AT TIME ZONE :zone AS date)
                   AND NOT EXISTS (
                       SELECT 1 FROM orders.customer_order o
                       WHERE o.customer = c.id
                         AND o.date + CAST(:retention AS interval)
                             > CAST(CAST(:now AS timestamptz) AT TIME ZONE :zone AS date)))
            """, nativeQuery = true)
    List<UUID> retentionMayBeOverBy(Instant now, String zone, String retention);
}
