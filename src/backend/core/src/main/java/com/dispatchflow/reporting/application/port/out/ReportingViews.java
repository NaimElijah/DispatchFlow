package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import java.util.List;
import java.util.Optional;

/**
 * Reporting's read port over the read-only views that Planning, Live Operations and Customers
 * &amp; Orders publish for it (DL §2.3 rule 3, ADR-041): what the summary queries need and no
 * module's Java API gives. No SQL behind it reads another module's tables, only their published
 * views.
 *
 * <p>Built in M5.
 */
public interface ReportingViews {

    /**
     * The plan of a date that the summary is about: the one published or completed. A cancelled
     * plan has no summary (FR-338).
     *
     * <p>Built in M5.
     *
     * @param date the date
     * @return the plan, or empty when no plan of the date is published or completed
     */
    Optional<PlanId> planOn(DeliveryDate date);

    /**
     * What a plan's vehicles actually drove, from Live Operations' published views (FR-106).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return the distance and the time driven, each if measured (Q-40)
     */
    ActualFigures actualsOf(PlanId plan);

    /**
     * Every failed stop and dropped order of the published or completed plans of a range of dates
     * (FR-161), from Planning's, Live Operations' and Customers &amp; Orders' published views. The
     * order's effective outcome counts (FR-333), and a cancelled order is left out (BR-30).
     *
     * <p>Built in M5.
     *
     * @param from the first date, included
     * @param to the last date, included; a range with {@code to} before {@code from} has none
     * @return the failures and drops, by date and then by order
     */
    List<FailureOrDrop> failuresAndDrops(DeliveryDate from, DeliveryDate to);
}
