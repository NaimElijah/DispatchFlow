package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.ReportReceipts;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.application.service.DeviceClock;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A driver starts their route (FR-68, FR-244, FR-327). The phone's outbox may send the start offline
 * (FR-70), with a request key, as it sends a report, so a resend gets the stored answer and changes
 * nothing (BR-28, NFR-65, ADR-087), and a key reused with another body is refused (NFR-130).
 *
 * <p>Outside calls first: the start's {@code CorrectedTime} from the {@link DeviceClock} and the
 * server's {@link Clock} (BR-114), Planning's {@link RouteMembership#driversAt} for the route's driver
 * at that time and now (ADR-114), and {@link PublishedRoutes} for the route's date. Then one
 * transaction creates the {@code RouteRun} through {@code RouteRun.start}, which raises {@code
 * RouteStarted}, with the receipt {@link
 * com.dispatchflow.liveops.application.port.out.RecordedReport.Started} and the audit record; a route
 * that already has a run is refused by the run itself. Like every changing call it writes its audit
 * record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M5; the request key in M8. Rows: FR-68, FR-70, FR-244, FR-327, BR-28, NFR-65, NFR-130.
 */
public final class StartRoute {

    private final RouteRunRepository runs;
    private final ReportReceipts receipts;
    private final RouteMembership membership;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param runs where route runs are kept
     * @param receipts the phone's idempotency record
     * @param membership Planning's answers on a route's drivers at a time
     * @param publishedRoutes Planning's routes in force, for the route's date
     * @param clock the server's time
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public StartRoute(
            RouteRunRepository runs,
            ReportReceipts receipts,
            RouteMembership membership,
            PublishedRoutes publishedRoutes,
            Clock clock,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.runs = runs;
        this.receipts = receipts;
        this.membership = membership;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Starts the route. A resend with the same key and body returns having changed nothing.
     *
     * <p>Built in M5; the request key in M8.
     *
     * @param actor the driver
     * @param route the route
     * @param time the device's time of the start and its clock when it sent it
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     DeviceTimeOutOfRange}; {@code NotYourRoute} when the driver is not both the route's driver
     *     at that time and now (BR-02, ADR-114); else {@code RouteAlreadyFinished} or {@code
     *     RunAlreadyStarted}
     * @throws NotFound when no plan in force holds the route
     * @throws IdempotencyKeyReused when the key was used before with another body (NFR-130)
     * @throws StaleVersion when another start or first report created the run meanwhile, or a
     *     concurrent send recorded the key first; the outbox retries with the same key
     */
    public void start(Actor actor, RouteId route, DeviceClock time, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
