package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.domain.DateCapacity;
import com.dispatchflow.orders.domain.Order;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the orders in {@code orders.customer_order}, keyed by their IDs. A date's capacity is read
 * under a transaction-scoped advisory lock keyed {@code orders:date-capacity:<date>} (ADR-127
 * decision 9), which the caller's transaction holds until it ends.
 */
@Component
@Transactional
class JpaOrderRepository implements OrderRepository {

    private final Orders orders;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param orders the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which {@code add} flushes
     */
    JpaOrderRepository(Orders orders, VersionedSave versionedSave, EntityManager entityManager) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<Order> get(OrderId id) {
        return orders.findById(id.value());
    }

    @Override
    public void add(Order order) {
        orders.save(order);
        entityManager.flush();
    }

    @Override
    public void save(Order order) {
        versionedSave.save(order, orders::save);
    }

    @Override
    public DateCapacity capacityUnderLock(DeliveryDate date) {
        orders.lockDate(date.value().toString());
        return DateCapacity.readUnderLock(
                date,
                orders.uncancelledRowsOn(date).stream()
                        .map(row ->
                                new DateCapacity.DatedOrder(OrderId.of(row.id()), Optional.ofNullable(row.reference())))
                        .toList());
    }

    @Override
    public List<OrderId> dueForAnonymization(LocalDate today) {
        DeliveryDate lastDue = DeliveryDate.of(today.minusDays(Order.RETENTION_DAYS));
        return orders.notAnonymizedUpTo(lastDue).stream().map(OrderId::of).toList();
    }

    @Override
    public List<Order> uncancelledOn(DeliveryDate date) {
        return orders.uncancelledOn(date);
    }

    @Override
    public List<OrderId> notAnonymizedOf(CustomerId customer) {
        return orders.notAnonymizedOf(customer).stream().map(OrderId::of).toList();
    }
}
