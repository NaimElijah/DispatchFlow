package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher confirms a report held for their decision: it becomes the stop's outcome, and every
 * other held report on the stop is refused as BR-90 says (BR-132, BR-135, BR-18, DL §8).
 *
 * <p>Outside calls first: Planning's {@link RouteMembership#driversAt} for the reporting route's
 * driver at the report's corrected time and now, and {@link PublishedRoutes} for its date and plan.
 * Then one transaction calls {@code Delivery.confirmHeld}, with a <em>report refused</em> alert for
 * each other held report its answer names as refused, so each is shown to the dispatcher as BR-90
 * shows a refused report (BR-135), and the audit record. As {@code RecordOutcomeOnBehalf} does, each
 * alert is stored with its plan's date. The same transaction creates the route's {@code RouteRun}
 * when this was the route's first report and its sender still drives it (ADR-114). Its {@code
 * OutcomeRecorded} reconciles the plan. Like every changing call it writes its audit record, a
 * refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>For each alert it raises, it calls {@link PushNotifier#alertStaff} after the commit, so a
 * staff phone app in the background learns of it (FR-405, ADR-083).
 *
 * <p>Built in M8; the push through {@link PushNotifier} in M10. Rows: BR-90, BR-132, BR-135.
 */
public final class ConfirmHeldReport {

    private final DeliveryRepository deliveries;
    private final RouteRunRepository runs;
    private final AlertRepository alerts;
    private final PushNotifier pushNotifier;
    private final RouteMembership membership;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param deliveries where deliveries are kept
     * @param runs where route runs are kept
     * @param alerts where alerts are kept
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param membership Planning's answers on a route's drivers at a time
     * @param publishedRoutes Planning's routes in force, for the route's date and plan
     * @param clock the server's time
     * @param ids new IDs, for the alerts
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public ConfirmHeldReport(
            DeliveryRepository deliveries,
            RouteRunRepository runs,
            AlertRepository alerts,
            PushNotifier pushNotifier,
            RouteMembership membership,
            PublishedRoutes publishedRoutes,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.deliveries = deliveries;
        this.runs = runs;
        this.alerts = alerts;
        this.pushNotifier = pushNotifier;
        this.membership = membership;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Confirms the held report, and alerts the dispatcher to each other held report it refuses.
     *
     * <p>Built in M8; the push in M10.
     *
     * @param actor the dispatcher
     * @param stop the stop the report is held on
     * @param report the held report
     * @param expected the delivery's version the dispatcher saw (BR-76)
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code ReportNotHeld} when
     *     the report is unknown, confirmed or refused, as when an outcome on the stop's current route
     *     overtook it meanwhile (DL §11)
     * @throws NotFound when nothing is recorded for the stop
     * @throws StaleVersion when the delivery changed since {@code expected}
     */
    public void confirm(Actor actor, StopId stop, HeldReportId report, Version expected) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
