package com.dispatchflow.liveops.application.service.delivery.dispatcher;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher refuses a report held for their decision, as BR-90 refuses a contradicting report
 * (BR-132, BR-135). The stop goes back to <em>arrived</em> or <em>pending</em>, or stays
 * <em>held</em> while another report is. {@link RefuseHeldReportChange} holds the one transaction,
 * which calls {@code Delivery.refuseHeld} with the audit record. Like every changing call it writes
 * its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110). After the commit,
 * it tells the open screens through {@link LiveUpdates}.
 *
 * <p>To Be Implemented. Rows: BR-132, BR-135.
 */
public final class RefuseHeldReport {

    private final LiveUpdates liveUpdates;
    private final RefuseHeldReportChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the open screens
     * @param change the use case's transaction
     */
    public RefuseHeldReport(LiveUpdates liveUpdates, RefuseHeldReportChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Refuses the held report.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param stop the stop the report is held on
     * @param report the held report
     * @param expected the delivery's version the dispatcher saw (BR-76)
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code ReportNotHeld} when
     *     the report is unknown, confirmed or refused
     * @throws NotFound when nothing is recorded for the stop
     * @throws StaleVersion when the delivery changed since {@code expected}
     */
    public void refuse(Actor actor, StopId stop, HeldReportId report, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
