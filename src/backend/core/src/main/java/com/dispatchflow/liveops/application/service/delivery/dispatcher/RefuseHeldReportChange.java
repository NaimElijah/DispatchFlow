package com.dispatchflow.liveops.application.service.delivery.dispatcher;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RefuseHeldReport} (BR-132, BR-135): it calls {@code
 * Delivery.refuseHeld} with the audit record (NFR-23).
 *
 * <p>Built in M8.
 */
@Transactional
public class RefuseHeldReportChange {

    private final DeliveryRepository deliveries;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param deliveries where deliveries are kept
     * @param clock the server's time
     * @param auditLog the audit trail
     */
    public RefuseHeldReportChange(DeliveryRepository deliveries, Clock clock, AuditLog auditLog) {
        this.deliveries = deliveries;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Refuses the held report and writes the audit record.
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
    public void apply(Actor actor, StopId stop, HeldReportId report, Version expected) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
