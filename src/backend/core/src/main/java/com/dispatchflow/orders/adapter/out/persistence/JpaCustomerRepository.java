package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.domain.Customer;
import com.dispatchflow.orders.domain.CustomerMatch;
import com.dispatchflow.orders.domain.NormalizedAddress;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Keeps the customers in {@code orders.customer}, keyed by their IDs, and their address flags in
 * {@code orders.customer_address_flag}. A customer's normalized address is a column beside its
 * address, so {@link #candidatesAt} is an indexed lookup, and its last delivery date is read from
 * the module's own {@code orders.customer_order}.
 */
@Component
class JpaCustomerRepository implements CustomerRepository {

    private final Customers customers;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param customers the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which {@code add} flushes
     */
    JpaCustomerRepository(Customers customers, VersionedSave versionedSave, EntityManager entityManager) {
        this.customers = Objects.requireNonNull(customers, "customers");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<Customer> get(CustomerId id) {
        return customers.findById(id.value());
    }

    @Override
    public void add(Customer customer) {
        customers.save(customer);
        entityManager.flush();
    }

    @Override
    public void save(Customer customer) {
        versionedSave.save(customer, customers::save);
    }

    @Override
    public List<CustomerMatch.Candidate> candidatesAt(NormalizedAddress address) {
        return customers.activeAt(address.text()).stream()
                .map(customer -> customer.asCandidate()
                        .orElseThrow(() -> new AssertionError("the lookup reads active customers only")))
                .toList();
    }

    @Override
    public Optional<DeliveryDate> lastDeliveryDateOf(CustomerId customer) {
        return customers
                .lastDeliveryDateOf(customer.value())
                .map(date -> StoredStateInvalid.whileRestoring(DeliveryDate.class, () -> DeliveryDate.of(date)));
    }

    @Override
    public List<CustomerId> dueForAnonymization(Instant now) {
        return customers.retentionMayBeOverBy(now, IsraelDateTime.ZONE.getId(), Customer.RETENTION.toString()).stream()
                .map(CustomerId::of)
                .toList();
    }
}
