package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.planning.application.api.PlanningSettingsFacts;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * The minute job that alerts the dispatcher when a route has not started 15 minutes after its
 * driver's shift start plus the departure allowance (FR-101, FR-67, DL §10.3). For today's routes in
 * force with no run, it builds each {@code StartDeadline} from the route driver's shift, read
 * through Fleet's {@link FleetFacts#driverOn}, and Planning's {@link
 * PlanningSettingsFacts#departureAllowance}, and raises a <em>route not started</em> alert once per
 * route. It reads {@code driverOn}, not {@code rosterOn}, which keeps only active drivers: a route
 * published before its driver lost the Driver role still has a deadline (ADR-062).
 *
 * <p>For each alert it raises, it calls {@link PushNotifier#alertStaff} after the commit, so a
 * staff phone app in the background learns of it (FR-405, ADR-083).
 *
 * <p>Built in M8; the push through {@link PushNotifier} in M10. Rows: FR-101.
 */
public final class AlertRoutesNotStarted {

    private final PublishedRoutes publishedRoutes;
    private final RouteRunRepository runs;
    private final FleetFacts fleetFacts;
    private final PlanningSettingsFacts planningSettings;
    private final AlertRepository alerts;
    private final PushNotifier pushNotifier;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the job.
     *
     * @param publishedRoutes Planning's routes in force, for today's routes and drivers
     * @param runs where route runs are kept
     * @param fleetFacts Fleet's roster, for each driver's shift
     * @param planningSettings Planning's departure allowance
     * @param alerts where alerts are kept
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param clock the server's time and today's date
     * @param ids new IDs, for the alerts
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public AlertRoutesNotStarted(
            PublishedRoutes publishedRoutes,
            RouteRunRepository runs,
            FleetFacts fleetFacts,
            PlanningSettingsFacts planningSettings,
            AlertRepository alerts,
            PushNotifier pushNotifier,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.publishedRoutes = publishedRoutes;
        this.runs = runs;
        this.fleetFacts = fleetFacts;
        this.planningSettings = planningSettings;
        this.alerts = alerts;
        this.pushNotifier = pushNotifier;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Raises the alerts now due.
     *
     * <p>Built in M8; the push in M10.
     *
     * @param call the job's server step
     * @return how many alerts were raised; one whose key was taken doesn't count
     */
    public int alertDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
