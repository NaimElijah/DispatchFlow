package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.domain.alert.DriverProblem;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;

/**
 * A driver reports a problem from either app (breakdown, running late, vehicle full), and the
 * dispatcher is alerted (FR-95). Planning's {@link PublishedRoutes} gives the route's plan and its
 * current driver, who alone may report on it (BR-02). One transaction raises the {@code Alert},
 * keyed on its own ID so every report alerts (ADR-107), with the audit record.
 *
 * <p>Built in M8. Rows: FR-95.
 */
public final class ReportProblem {

    private final AlertRepository alerts;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param alerts where alerts are kept
     * @param publishedRoutes Planning's routes in force, for the route's plan and driver
     * @param clock the server's time
     * @param ids new IDs, for the alert
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public ReportProblem(
            AlertRepository alerts,
            PublishedRoutes publishedRoutes,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.alerts = alerts;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Raises the dispatcher's alert for the problem.
     *
     * <p>Built in M8.
     *
     * @param actor the driver
     * @param route the driver's route
     * @param problem what the problem is
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code NotYourRoute} when
     *     the acting driver does not drive the route now (BR-02)
     * @throws NotFound when no plan in force holds the route
     */
    public void report(Actor actor, RouteId route, DriverProblem problem) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
