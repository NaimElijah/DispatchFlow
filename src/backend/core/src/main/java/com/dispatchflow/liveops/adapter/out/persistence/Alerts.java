package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.domain.alert.Alert;
import com.dispatchflow.liveops.domain.alert.DedupeKey;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaAlertRepository}: {@code save}, the lookups the port
 * needs, the dedupe key's lock, and the native statement that keeps the delivery date beside each
 * alert.
 */
interface Alerts extends Repository<Alert, UUID> {

    /**
     * Saves an alert and publishes its pending events.
     *
     * @param alert the alert
     * @return the managed alert
     */
    Alert save(Alert alert);

    /**
     * Loads an alert.
     *
     * @param alert the alert's UUID
     * @return the alert, or empty
     */
    Optional<Alert> findById(UUID alert);

    /**
     * Whether an alert, open or acknowledged, holds a dedupe key.
     *
     * @param key the key
     * @return {@code true} when one does
     */
    boolean existsByDedupeKey(DedupeKey key);

    /**
     * Takes a dedupe key's lock for the caller's transaction, waiting for it (ADR-127 decision 9),
     * so two raises of one key run one after the other.
     *
     * @param key the key's text
     * @return 1, always: the call is made for its lock
     */
    @Query(
            value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext('liveops:alert-dedupe-key:' || :key))",
            nativeQuery = true)
    int lockKey(String key);

    /**
     * Keeps the delivery date of an alert just stored, a column the alert does not map.
     *
     * @param alert the alert's UUID
     * @param date the delivery date of the alert's plan
     * @return 1 when the alert was stored without its date, 0 otherwise
     */
    @Modifying
    @Query(
            value = "UPDATE liveops.alert SET delivery_date = :date WHERE id = :alert AND delivery_date IS NULL",
            nativeQuery = true)
    int keepDate(UUID alert, LocalDate date);
}
