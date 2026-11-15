package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * The minute job that alerts the dispatcher on stops at risk of missing their window: projected
 * later than the window end less 15 minutes, never a leave-at-door stop (FR-34, FR-253, FR-369,
 * ADR-067). For today's routes in force it builds the {@code PlannedLegs} from Planning's {@link
 * PublishedRoutes} and Customers &amp; Orders' {@link OrdersForDate}, the {@code StopFact}s from the
 * deliveries and the runs' starts, asks {@code ArrivalProjection.isAtRisk}, and raises a <em>stop at
 * risk</em> alert keyed on the revision, so a new revision can alert again (ADR-107).
 *
 * <p>For each alert it raises, it calls {@link PushNotifier#alertStaff} after the commit, so a
 * staff phone app in the background learns of it (FR-405, ADR-083).
 *
 * <p>Built in M8. Rows: FR-34, FR-253, ADR-067.
 */
public final class AlertStopsAtRisk {

    private final PublishedRoutes publishedRoutes;
    private final OrdersForDate ordersForDate;
    private final DeliveryRepository deliveries;
    private final RouteRunRepository runs;
    private final AlertRepository alerts;
    private final PushNotifier pushNotifier;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the job.
     *
     * @param publishedRoutes Planning's routes in force and their stored legs
     * @param ordersForDate Customers &amp; Orders' windows and door flags
     * @param deliveries where deliveries are kept
     * @param runs where route runs are kept
     * @param alerts where alerts are kept
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param clock the server's time and today's date
     * @param ids new IDs, for the alerts
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public AlertStopsAtRisk(
            PublishedRoutes publishedRoutes,
            OrdersForDate ordersForDate,
            DeliveryRepository deliveries,
            RouteRunRepository runs,
            AlertRepository alerts,
            PushNotifier pushNotifier,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.publishedRoutes = publishedRoutes;
        this.ordersForDate = ordersForDate;
        this.deliveries = deliveries;
        this.runs = runs;
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
     * <p>Built in M8.
     *
     * @param call the job's server step
     * @return how many alerts were raised; one whose key was taken doesn't count
     */
    public int checkDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
