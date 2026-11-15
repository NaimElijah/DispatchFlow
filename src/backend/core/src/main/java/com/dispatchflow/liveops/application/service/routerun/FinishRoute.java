package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.application.port.out.ReportReceipts;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.application.service.DeviceClock;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.liveops.application.service.RouteNotStarted;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A driver finishes their route, perhaps with stops pending after a warning, and the dispatcher is
 * alerted when stops are (FR-68, FR-329, ADR-107). The phone's outbox may send the finish offline
 * (FR-70), with a request key, as it sends a report, so a resend gets the stored answer and changes
 * nothing (BR-28, NFR-65, ADR-087), and a key reused with another body is refused (NFR-130).
 *
 * <p>Outside calls first: the finish's {@code CorrectedTime} (BR-114), Planning's {@link
 * RouteMembership#driversAt} for the route's drivers then and now (ADR-114), and {@link
 * PublishedRoutes} for the route's stops in order, whose deliveries tell which have no outcome. A
 * route with no run is refused with {@link RouteNotStarted} (ADR-107). Then one transaction calls
 * {@code RouteRun.finish}, with the <em>route finished with stops pending</em> alert when any are,
 * the receipt {@link com.dispatchflow.liveops.application.port.out.RecordedReport.Finished} and the
 * audit record. A late report after the finish is still accepted and changes no run (ADR-107). Like
 * every changing call it writes its audit record, a refused attempt's in its own transaction
 * (NFR-23, ADR-110).
 *
 * <p>The alert is stored with its plan's date from {@link PublishedRoutes}. For each alert it raises,
 * it calls {@link PushNotifier#alertStaff} after the commit, so a staff phone app in the background
 * learns of it (FR-405, ADR-083).
 *
 * <p>Built in M5; the request key and the pending-stops alert through {@link AlertRepository} in M8,
 * and the push through {@link PushNotifier} in M10. Rows: FR-68, FR-70, FR-329, BR-28, NFR-65,
 * NFR-130, ADR-107.
 */
public final class FinishRoute {

    private final RouteRunRepository runs;
    private final DeliveryRepository deliveries;
    private final AlertRepository alerts;
    private final ReportReceipts receipts;
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
     * @param runs where route runs are kept
     * @param deliveries where deliveries are kept, for the pending stops
     * @param alerts where alerts are kept
     * @param receipts the phone's idempotency record
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param membership Planning's answers on a route's drivers at a time
     * @param publishedRoutes Planning's routes in force, for the route's stops, plan and date
     * @param clock the server's time
     * @param ids new IDs, for the alert
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public FinishRoute(
            RouteRunRepository runs,
            DeliveryRepository deliveries,
            AlertRepository alerts,
            ReportReceipts receipts,
            PushNotifier pushNotifier,
            RouteMembership membership,
            PublishedRoutes publishedRoutes,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.runs = runs;
        this.deliveries = deliveries;
        this.alerts = alerts;
        this.receipts = receipts;
        this.pushNotifier = pushNotifier;
        this.membership = membership;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Finishes the route. A resend with the same key and body returns having changed nothing.
     *
     * <p>Built in M5; the request key and the alert in M8, and the push in M10.
     *
     * @param actor the driver
     * @param route the route
     * @param time the device's time of the finish and its clock when it sent it
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     DeviceTimeOutOfRange}; {@code NotYourRoute} when the driver is not both the route's driver
     *     at that time and now (BR-02, ADR-114); else {@code RouteAlreadyFinished}
     * @throws NotFound when no plan in force holds the route
     * @throws RouteNotStarted when the route has no run (ADR-107)
     * @throws IdempotencyKeyReused when the key was used before with another body (NFR-130)
     * @throws StaleVersion when another writer saved the run meanwhile, or a concurrent send
     *     recorded the key first; the outbox retries with the same key
     */
    public void finish(Actor actor, RouteId route, DeviceClock time, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
