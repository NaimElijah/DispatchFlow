package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.alert.Alert;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * Where alerts are kept, each unique by its dedupe key (DL §3.6, §4.5, FR-157, ADR-107). The key is
 * backed by a unique constraint, so an alert raised twice, as by a retried listener or a job that
 * runs every minute, is stored once, and an acknowledged key is never raised again.
 *
 * <p>Built in M8.
 */
public interface AlertRepository {

    /**
     * One alert.
     *
     * <p>Built in M8.
     *
     * @param alert the alert's ID
     * @return the alert, or empty when no alert has the ID
     */
    Optional<Alert> get(AlertId alert);

    /**
     * Stores a new alert unless an alert with its dedupe key exists, open or acknowledged (ADR-107),
     * in the caller's transaction.
     *
     * <p>Built in M8.
     *
     * @param alert the raised alert, at {@code Version.NEW}
     * @return {@code true} when it was stored, {@code false} when its key was taken, so nothing
     *     changed
     */
    boolean addUnlessKeyTaken(Alert alert);

    /**
     * Stores a changed alert, checking the version it was loaded at (BR-76, ADR-057).
     *
     * <p>Built in M8.
     *
     * @param alert the alert
     * @throws StaleVersion when another dispatcher saved it since it was loaded
     */
    void save(Alert alert);
}
