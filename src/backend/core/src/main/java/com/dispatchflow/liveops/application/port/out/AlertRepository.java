package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.alert.Alert;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.DeliveryDate;
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
     * Stores a new alert unless an alert with its dedupe key exists, open or acknowledged
     * (ADR-107), in the caller's transaction.
     *
     * <p>An alert names its plan but not the plan's date, and no SQL reads Planning's schema (DL
     * §2.3 rule 3), so the caller passes the date from Planning's answer and the adapter keeps it
     * beside the alert. {@link LiveOpsViews#unacknowledgedAt} reads it to tell whether the plan has
     * completed (FR-261, BR-102).
     *
     * <p>Built in M8.
     *
     * @param alert the raised alert, never saved
     * @param date the delivery date of the alert's plan, from Planning's answer: {@code
     *     PublishedRoutes} for a plan in force, or the {@code MembershipFact} of a reported stop,
     *     whose plan may since have been cancelled
     * @return {@code true} when it was stored, {@code false} when its key was taken, so nothing
     *     changed
     */
    boolean addUnlessKeyTaken(Alert alert, DeliveryDate date);

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
