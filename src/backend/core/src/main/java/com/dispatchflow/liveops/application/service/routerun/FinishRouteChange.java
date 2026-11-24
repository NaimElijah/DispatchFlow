package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.ReportReceipts;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.application.service.DeviceClock;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.liveops.application.service.RouteNotStarted;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link FinishRoute} (FR-68, FR-329, ADR-107): it corrects the finish's time
 * (BR-114), reads the route's drivers then and now from Planning's {@link RouteMembership#driversAt}
 * (ADR-114) and its stops from {@link PublishedRoutes}, and calls {@code RouteRun.finish}, with the
 * <em>route finished with stops pending</em> alert when any are, the receipt {@link
 * com.dispatchflow.liveops.application.port.out.RecordedReport.Finished} and the audit record
 * (NFR-23). A resend with the same key and body gets the stored receipt and changes nothing (BR-28,
 * NFR-65).
 *
 * <p>Built in M5; the request key and the pending-stops alert in M8.
 */
@Transactional
public class FinishRouteChange {

    private final RouteRunRepository runs;
    private final DeliveryRepository deliveries;
    private final AlertRepository alerts;
    private final ReportReceipts receipts;
    private final RouteMembership membership;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param runs where route runs are kept
     * @param deliveries where deliveries are kept, for the pending stops
     * @param alerts where alerts are kept
     * @param receipts the phone's idempotency record
     * @param membership Planning's answers on a route's drivers at a time
     * @param publishedRoutes Planning's routes in force, for the route's stops, plan and date
     * @param clock the server's time
     * @param ids new IDs, for the alert
     * @param auditLog the audit trail
     */
    public FinishRouteChange(
            RouteRunRepository runs,
            DeliveryRepository deliveries,
            AlertRepository alerts,
            ReportReceipts receipts,
            RouteMembership membership,
            PublishedRoutes publishedRoutes,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.runs = runs;
        this.deliveries = deliveries;
        this.alerts = alerts;
        this.receipts = receipts;
        this.membership = membership;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Finishes the route, with the receipt and the audit record.
     *
     * <p>Built in M5; the request key and the alert in M8.
     *
     * @param actor the driver
     * @param route the route
     * @param time the device's time of the finish and its clock when it sent it
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @return the pending-stops alert raised, or empty when no stop is pending or the call is a
     *     resend whose stored answer it returns
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     DeviceTimeOutOfRange}; {@code NotYourRoute} when the driver is not both the route's
     *     driver at that time and now (BR-02, ADR-114); else {@code RouteAlreadyFinished}
     * @throws NotFound when no plan in force holds the route
     * @throws RouteNotStarted when the route has no run (ADR-107)
     * @throws IdempotencyKeyReused when the key was used before with another body, or its stored
     *     receipt is another call's (NFR-130)
     * @throws StaleVersion when another writer saved the run meanwhile, or a concurrent send
     *     recorded the key first
     */
    public Optional<AlertId> apply(
            Actor actor, RouteId route, DeviceClock time, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
