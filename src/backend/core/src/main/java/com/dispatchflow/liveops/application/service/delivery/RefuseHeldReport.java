package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher refuses a report held for their decision, as BR-90 refuses a contradicting report
 * (BR-132, BR-135). The stop goes back to <em>arrived</em> or <em>pending</em>, or stays
 * <em>held</em> while another report is. One transaction calls {@code Delivery.refuseHeld} with the
 * audit record.
 *
 * <p>Built in M8. Rows: BR-132, BR-135.
 */
public final class RefuseHeldReport {

    private final DeliveryRepository deliveries;
    private final Clock clock;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param deliveries where deliveries are kept
     * @param clock the server's time
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public RefuseHeldReport(DeliveryRepository deliveries, Clock clock, AuditLog auditLog, LiveUpdates liveUpdates) {
        this.deliveries = deliveries;
        this.clock = clock;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Refuses the held report.
     *
     * <p>Built in M8.
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
        throw new UnsupportedOperationException("Built in M8");
    }
}
