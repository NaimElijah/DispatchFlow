package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * The minute job that alerts the dispatcher when a driver on a started route hasn't confirmed a
 * change 10 minutes after its revision took effect (FR-97, FR-157, FR-250, ADR-107, DL §12). For
 * today's runs it asks {@code RouteRun.isConfirmationOverdue} with the revision and time Planning's
 * {@link PublishedRoutes} gives each route, and raises a <em>route change unconfirmed</em> alert,
 * keyed on the revision, so each overdue revision alerts once.
 *
 * <p>Built in M8. Rows: FR-157, FR-250, ADR-107.
 */
public final class AlertUnconfirmedChanges {

    private final RouteRunRepository runs;
    private final PublishedRoutes publishedRoutes;
    private final AlertRepository alerts;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the job.
     *
     * @param runs where route runs are kept
     * @param publishedRoutes Planning's routes in force, for each route's last change
     * @param alerts where alerts are kept
     * @param clock the server's time and today's date
     * @param ids new IDs, for the alerts
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public AlertUnconfirmedChanges(
            RouteRunRepository runs,
            PublishedRoutes publishedRoutes,
            AlertRepository alerts,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.runs = runs;
        this.publishedRoutes = publishedRoutes;
        this.alerts = alerts;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Raises the alerts now due.
     *
     * <p>Built in M8.
     *
     * @param call the job's server step
     * @return how many alerts were raised; one whose key was taken doesn't count
     */
    public int checkDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
