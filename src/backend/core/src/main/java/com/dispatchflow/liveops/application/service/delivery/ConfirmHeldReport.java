package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher confirms a report held for their decision: it becomes the stop's outcome, and every
 * other held report on the stop is refused as BR-90 says (BR-132, BR-135, BR-18, DL §8).
 *
 * <p>Outside calls first: Planning's {@link RouteMembership#driversAt} for the reporting route's
 * driver at the report's corrected time and now, and {@link PublishedRoutes} for its date. Then one
 * transaction calls {@code Delivery.confirmHeld} with the audit record, and creates the route's
 * {@code RouteRun} when this was the route's first report and its sender still drives it (ADR-114).
 * Its {@code OutcomeRecorded} reconciles the plan.
 *
 * <p>Built in M8. Rows: BR-132, BR-135.
 */
public final class ConfirmHeldReport {

    private final DeliveryRepository deliveries;
    private final RouteRunRepository runs;
    private final RouteMembership membership;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param deliveries where deliveries are kept
     * @param runs where route runs are kept
     * @param membership Planning's answers on a route's drivers at a time
     * @param publishedRoutes Planning's routes in force, for the route's date
     * @param clock the server's time
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public ConfirmHeldReport(
            DeliveryRepository deliveries,
            RouteRunRepository runs,
            RouteMembership membership,
            PublishedRoutes publishedRoutes,
            Clock clock,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.deliveries = deliveries;
        this.runs = runs;
        this.membership = membership;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Confirms the held report.
     *
     * <p>Built in M8.
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
