package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link AlertUnconfirmedChanges} (FR-157, FR-250, ADR-107): for today's runs it
 * asks {@code RouteRun.isConfirmationOverdue} and raises a <em>route change unconfirmed</em> alert
 * keyed on the revision, with the audit record (NFR-23).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class AlertUnconfirmedChangesChange {

    private final RouteRunRepository runs;
    private final PublishedRoutes publishedRoutes;
    private final AlertRepository alerts;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param runs where route runs are kept
     * @param publishedRoutes Planning's routes in force, for each route's last change
     * @param alerts where alerts are kept
     * @param clock the server's time and today's date
     * @param ids new IDs, for the alerts
     * @param auditLog the audit trail
     */
    public AlertUnconfirmedChangesChange(
            RouteRunRepository runs,
            PublishedRoutes publishedRoutes,
            AlertRepository alerts,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.runs = runs;
        this.publishedRoutes = publishedRoutes;
        this.alerts = alerts;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Raises the alerts now due and writes the audit record.
     *
     * <p>To Be Implemented.
     *
     * @param call the job's server step
     * @return the alerts raised; none whose key was taken, and none when no confirmation is overdue
     */
    public List<AlertId> apply(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
