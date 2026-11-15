package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.domain.delivery.DispatcherOutcome;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher records a stop's outcome on its driver's behalf, as when the driver's phone can't
 * (FR-73, FR-257, FR-368). It needs no proof (FR-367) and no entitlement, starts no route run, and
 * overtakes every held report, each shown to the dispatcher (BR-134, ADR-107).
 *
 * <p>Outside calls first: Planning's {@link PublishedRoutes} for the route's stop, order and
 * attempt and its plan with its date, which each alert is stored with, and Customers &amp; Orders'
 * {@link StopOrders} for the door flag. Then one transaction calls {@code Delivery.recordOnBehalf},
 * creating the delivery on its first fact, with an alert per overtaken held report and the audit
 * record. Its {@code OutcomeRecorded} reconciles the plan (DL §8). Like every changing call it
 * writes its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>For each alert it raises, it calls {@link PushNotifier#alertStaff} after the commit, so a
 * staff phone app in the background learns of it (FR-405, ADR-083).
 *
 * <p>Built in M8; the push through {@link PushNotifier} in M10. Rows: FR-73, FR-257, FR-368, BR-134.
 */
public final class RecordOutcomeOnBehalf {

    private final DeliveryRepository deliveries;
    private final AlertRepository alerts;
    private final PushNotifier pushNotifier;
    private final PublishedRoutes publishedRoutes;
    private final StopOrders stopOrders;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param deliveries where deliveries are kept
     * @param alerts where alerts are kept
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param publishedRoutes Planning's routes in force
     * @param stopOrders Customers &amp; Orders' door flag of an order
     * @param clock the server's time, which the outcome counts from
     * @param ids new IDs, for the alerts
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public RecordOutcomeOnBehalf(
            DeliveryRepository deliveries,
            AlertRepository alerts,
            PushNotifier pushNotifier,
            PublishedRoutes publishedRoutes,
            StopOrders stopOrders,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.deliveries = deliveries;
        this.alerts = alerts;
        this.pushNotifier = pushNotifier;
        this.publishedRoutes = publishedRoutes;
        this.stopOrders = stopOrders;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Records the outcome, counting from now.
     *
     * <p>Built in M8; the push in M10.
     *
     * @param actor the dispatcher
     * @param stop the stop
     * @param route the route whose driver it is recorded for
     * @param outcome what the dispatcher records; a failure carries its reason (FR-73)
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     NotALeaveAtDoorOrder}, then {@code SameOutcomeAlreadyRecorded} or {@code
     *     OutcomeAlreadyRecorded} (BR-18)
     * @throws NotFound when no plan in force holds the route, or the route doesn't hold the stop
     * @throws StaleVersion when another writer saved the stop's delivery meanwhile
     */
    public void record(Actor actor, StopId stop, RouteId route, DispatcherOutcome outcome) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
