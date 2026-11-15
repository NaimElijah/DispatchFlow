package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.Customer;
import com.dispatchflow.orders.domain.CustomerMatch;
import com.dispatchflow.orders.domain.NormalizedAddress;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where customers are kept, as whole {@link Customer} aggregates (DL §3.6, §4.3), with the lookups
 * the use cases need. It has no query for screens: {@link OrdersViews} serves those.
 *
 * <p>Built in M2, and in M13 for the retention lookups.
 */
public interface CustomerRepository {

    /**
     * Loads a customer, whatever its state.
     *
     * <p>Built in M2.
     *
     * @param id the customer's ID
     * @return the customer at its stored version, or empty when none has the ID
     */
    Optional<Customer> get(CustomerId id);

    /**
     * Stores a new customer, in the caller's transaction.
     *
     * <p>Built in M2.
     *
     * @param customer the customer, at {@code Version.NEW}
     */
    void add(Customer customer);

    /**
     * Stores a changed customer, in the caller's transaction, if its stored version is still the
     * one it was loaded at (BR-76, ADR-057).
     *
     * <p>Built in M2.
     *
     * @param customer the customer
     * @throws StaleVersion when another change was saved since it was loaded
     */
    void save(Customer customer);

    /**
     * The active customers whose normalized address equals one, which an import row is matched
     * against (FR-60, FR-310 to FR-312). A retired or anonymized customer is never a candidate.
     *
     * <p>Built in M2.
     *
     * @param address the row's normalized address (FR-398)
     * @return the candidates, each once; empty when none has the address
     */
    List<CustomerMatch.Candidate> candidatesAt(NormalizedAddress address);

    /**
     * A customer's last delivery date: the latest delivery date of any of its orders, from which a
     * customer never retired counts its retention (BR-98, NFR-257, ADR-102).
     *
     * <p>Built in M13.
     *
     * @param customer the customer
     * @return the latest delivery date of any of its orders, cancelled ones included, or empty when
     *     it has none
     */
    Optional<DeliveryDate> lastDeliveryDateOf(CustomerId customer);

    /**
     * The customers whose retention may be over at an instant: retired a year before it, or, never
     * retired, with neither a creation nor an order within the year before it (BR-98, NFR-257,
     * ADR-075). {@link Customer#anonymize} decides each one, in Israel's calendar.
     *
     * <p>Built in M13.
     *
     * @param now the time of the retention job
     * @return the customers to try, each once; an anonymized one is never among them
     */
    List<CustomerId> dueForAnonymization(Instant now);
}
