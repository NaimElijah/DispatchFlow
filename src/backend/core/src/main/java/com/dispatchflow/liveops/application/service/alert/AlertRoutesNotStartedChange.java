package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.planning.application.api.PlanningSettingsFacts;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link AlertRoutesNotStarted} (FR-101): for today's routes in force with no
 * run, it builds each {@code StartDeadline} and raises a <em>route not started</em> alert once per
 * route past it, with the audit record (NFR-23).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class AlertRoutesNotStartedChange {

    private final PublishedRoutes publishedRoutes;
    private final RouteRunRepository runs;
    private final FleetFacts fleetFacts;
    private final PlanningSettingsFacts planningSettings;
    private final AlertRepository alerts;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param publishedRoutes Planning's routes in force, for today's routes and drivers
     * @param runs where route runs are kept
     * @param fleetFacts Fleet's roster, for each driver's shift
     * @param planningSettings Planning's departure allowance
     * @param alerts where alerts are kept
     * @param clock the server's time and today's date
     * @param ids new IDs, for the alerts
     * @param auditLog the audit trail
     */
    public AlertRoutesNotStartedChange(
            PublishedRoutes publishedRoutes,
            RouteRunRepository runs,
            FleetFacts fleetFacts,
            PlanningSettingsFacts planningSettings,
            AlertRepository alerts,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.publishedRoutes = publishedRoutes;
        this.runs = runs;
        this.fleetFacts = fleetFacts;
        this.planningSettings = planningSettings;
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
     * @return the alerts raised; none whose key was taken, and none when no route is past its
     *     deadline
     */
    public List<AlertId> apply(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
