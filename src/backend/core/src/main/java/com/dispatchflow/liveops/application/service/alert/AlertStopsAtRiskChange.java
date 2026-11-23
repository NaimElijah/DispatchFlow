package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link AlertStopsAtRisk} (FR-34, FR-253, ADR-067): for today's routes in force
 * it asks {@code ArrivalProjection.isAtRisk} of each stop and raises a <em>stop at risk</em> alert
 * keyed on the revision, with the audit record (ADR-107, NFR-23).
 *
 * <p>Built in M8.
 */
@Transactional
public class AlertStopsAtRiskChange {

    private final PublishedRoutes publishedRoutes;
    private final DeliveryRepository deliveries;
    private final RouteRunRepository runs;
    private final AlertRepository alerts;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param publishedRoutes Planning's routes in force, their stored legs and their stops' windows
     * @param deliveries where deliveries are kept
     * @param runs where route runs are kept
     * @param alerts where alerts are kept
     * @param clock the server's time and today's date
     * @param ids new IDs, for the alerts
     * @param auditLog the audit trail
     */
    public AlertStopsAtRiskChange(
            PublishedRoutes publishedRoutes,
            DeliveryRepository deliveries,
            RouteRunRepository runs,
            AlertRepository alerts,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.publishedRoutes = publishedRoutes;
        this.deliveries = deliveries;
        this.runs = runs;
        this.alerts = alerts;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Raises the alerts now due and writes the audit record.
     *
     * <p>Built in M8.
     *
     * @param call the job's server step
     * @return the alerts raised; none whose key was taken, and none when no stop is at risk
     */
    public List<AlertId> apply(CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
