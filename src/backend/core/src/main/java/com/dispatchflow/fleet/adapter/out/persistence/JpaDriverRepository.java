package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.fleet.domain.driver.Driver;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the drivers in {@code fleet.driver}, keyed by their accounts, and their weekly shifts in
 * {@code fleet.driver_weekly_shift}.
 *
 * <p>The registration order is the {@code registration_order} column, a number the database draws
 * when it inserts a driver's row, so a driver registered later sorts later. The entity doesn't map
 * it: no rule reads it, and only {@link #registered()} orders by it.
 */
@Component
@Transactional
class JpaDriverRepository implements DriverRepository {

    private final Drivers drivers;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param drivers the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which {@code add} flushes
     */
    JpaDriverRepository(Drivers drivers, VersionedSave versionedSave, EntityManager entityManager) {
        this.drivers = Objects.requireNonNull(drivers, "drivers");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<Driver> get(AccountId id) {
        return drivers.findById(id.value());
    }

    /**
     * {@inheritDoc}
     *
     * <p>It reads the keys in order first, then loads those drivers with their weekly shifts in one
     * query, so reading them costs two queries whatever the number of drivers.
     */
    @Override
    public List<Driver> registered() {
        List<UUID> order = drivers.inRegistrationOrder();
        if (order.isEmpty()) {
            return List.of();
        }
        Map<UUID, Driver> loaded = drivers.findByIdIn(order).stream()
                .collect(Collectors.toMap(driver -> driver.id().value(), Function.identity()));
        return order.stream().map(loaded::get).toList();
    }

    @Override
    public List<AccountId> dueForAnonymization(Instant now) {
        return drivers.retentionEndedBy(now, IsraelDateTime.ZONE.getId(), Driver.RETENTION.toString()).stream()
                .map(AccountId::of)
                .toList();
    }

    @Override
    public void add(Driver driver) {
        drivers.save(driver);
        entityManager.flush();
    }

    @Override
    public void save(Driver driver) {
        versionedSave.save(driver, drivers::save);
    }
}
