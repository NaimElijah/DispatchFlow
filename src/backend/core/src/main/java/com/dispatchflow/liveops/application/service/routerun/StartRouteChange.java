package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link StartRoute} (FR-68, FR-244, FR-327): it corrects the start's time from
 * the {@link DeviceClock} and the server's {@link Clock} (BR-114), reads the route's driver then and
 * now from Planning's {@link RouteMembership#driversAt} (ADR-114) and its date from {@link
 * PublishedRoutes}, and creates the {@code RouteRun} through {@code RouteRun.start}, with the
 * receipt {@link com.dispatchflow.liveops.application.port.out.RecordedReport.Started} and the audit
 * record (NFR-23). A resend with the same key and body gets the stored receipt and changes nothing
 * (BR-28, NFR-65).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class StartRouteChange {

    private final RouteRunRepository runs;
    private final ReportReceipts receipts;
    private final RouteMembership membership;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param runs where route runs are kept
     * @param receipts the phone's idempotency record
     * @param membership Planning's answers on a route's drivers at a time
     * @param publishedRoutes Planning's routes in force, for the route's date
     * @param clock the server's time
     * @param auditLog the audit trail
     */
    public StartRouteChange(
            RouteRunRepository runs,
            ReportReceipts receipts,
            RouteMembership membership,
            PublishedRoutes publishedRoutes,
            Clock clock,
            AuditLog auditLog) {
        this.runs = runs;
        this.receipts = receipts;
        this.membership = membership;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Starts the route, with the receipt and the audit record.
     *
     * <p>To Be Implemented.
     *
     * @param actor the driver
     * @param route the route
     * @param time the device's time of the start and its clock when it sent it
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     DeviceTimeOutOfRange}; {@code NotYourRoute} when the driver is not both the route's
     *     driver at that time and now (BR-02, ADR-114); else {@code RouteAlreadyFinished} or {@code
     *     RunAlreadyStarted}
     * @throws NotFound when no plan in force holds the route
     * @throws IdempotencyKeyReused when the key was used before with another body, or its stored
     *     receipt is another call's (NFR-130)
     * @throws StaleVersion when another start or first report created the run meanwhile, or a
     *     concurrent send recorded the key first
     */
    public void apply(Actor actor, RouteId route, DeviceClock time, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
