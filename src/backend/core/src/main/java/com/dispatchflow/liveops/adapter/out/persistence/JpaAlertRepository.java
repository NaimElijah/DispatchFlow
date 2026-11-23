package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.domain.alert.Alert;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps alerts in {@code liveops.alert}, each unique by its dedupe key.
 *
 * <p>{@link #addUnlessKeyTaken} takes the key's advisory lock, then looks for the key, and stores
 * the alert only when no alert holds it. A taken key is found by a query, not by a failed insert,
 * so the caller's transaction stays usable, and the alert's save, which publishes its events, never
 * runs. The lock makes two raises of one key run one after the other, so the second finds the
 * first; the unique constraint on the key is the backstop, should a write ever skip the lock.
 *
 * <p>The plan's delivery date is a column of the alert's row that the alert does not map: the add
 * writes it with one native {@code UPDATE} once the alert is inserted, in the same transaction. The
 * column is nullable for that reason; only this adapter writes the table.
 */
@Component
@Transactional
class JpaAlertRepository implements AlertRepository {

    private final Alerts alerts;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param alerts the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which the add flushes
     */
    JpaAlertRepository(Alerts alerts, VersionedSave versionedSave, EntityManager entityManager) {
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<Alert> get(AlertId alert) {
        return alerts.findById(alert.value());
    }

    /**
     * {@inheritDoc}
     *
     * @throws AssertionError when the alert was stored before, so its row already has a date: the
     *     caller added an alert it had loaded
     */
    @Override
    public boolean addUnlessKeyTaken(Alert alert, DeliveryDate date) {
        Objects.requireNonNull(date, "date");
        alerts.lockKey(alert.dedupeKey().value());
        if (alerts.existsByDedupeKey(alert.dedupeKey())) {
            return false;
        }
        alerts.save(alert);
        entityManager.flush();
        if (alerts.keepDate(alert.id().value(), date.value()) != 1) {
            throw new AssertionError("an alert is added once, and its date kept with it");
        }
        return true;
    }

    @Override
    public void save(Alert alert) {
        versionedSave.save(alert, alerts::save);
    }
}
