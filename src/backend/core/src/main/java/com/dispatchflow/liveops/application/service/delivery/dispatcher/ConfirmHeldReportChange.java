package com.dispatchflow.liveops.application.service.delivery.dispatcher;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link ConfirmHeldReport} (BR-132, BR-135, DL §8): it reads the reporting
 * route's driver then and now from Planning's {@link RouteMembership#driversAt} and its date and
 * plan from {@link PublishedRoutes}, and calls {@code Delivery.confirmHeld}, with a <em>report
 * refused</em> alert for each other held report it refuses, the route's first {@code RouteRun} when
 * due (ADR-114), and the audit record (NFR-23).
 *
 * <p>Built in M8.
 */
@Transactional
public class ConfirmHeldReportChange {

    private final DeliveryRepository deliveries;
    private final RouteRunRepository runs;
    private final AlertRepository alerts;
    private final RouteMembership membership;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param deliveries where deliveries are kept
     * @param runs where route runs are kept
     * @param alerts where alerts are kept
     * @param membership Planning's answers on a route's drivers at a time
     * @param publishedRoutes Planning's routes in force, for the route's date and plan
     * @param clock the server's time
     * @param ids new IDs, for the alerts
     * @param auditLog the audit trail
     */
    public ConfirmHeldReportChange(
            DeliveryRepository deliveries,
            RouteRunRepository runs,
            AlertRepository alerts,
            RouteMembership membership,
            PublishedRoutes publishedRoutes,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.deliveries = deliveries;
        this.runs = runs;
        this.alerts = alerts;
        this.membership = membership;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Confirms the held report, alerting the dispatcher to each other held report it refuses, and
     * writes the audit record.
     *
     * <p>Built in M8.
     *
     * @param actor the dispatcher
     * @param stop the stop the report is held on
     * @param report the held report
     * @param expected the delivery's version the dispatcher saw (BR-76)
     * @return the alerts raised; none when no other report was held
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code ReportNotHeld} when
     *     the report is unknown, confirmed or refused (DL §11)
     * @throws NotFound when nothing is recorded for the stop
     * @throws StaleVersion when the delivery changed since {@code expected}
     */
    public List<AlertId> apply(Actor actor, StopId stop, HeldReportId report, Version expected) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
