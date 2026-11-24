package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.service.DeviceClock;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A driver starts their route (FR-68, FR-244, FR-327). The phone's outbox may send the start
 * offline (FR-70), with a request key, as it sends a report, so a resend gets the stored answer and
 * changes nothing (BR-28, NFR-65, ADR-087), and a key reused with another body is refused
 * (NFR-130).
 *
 * <p>{@link StartRouteChange#apply} holds the transaction, and reads first: the start's {@code
 * CorrectedTime} from the {@link DeviceClock} and the server's clock (BR-114), Planning's {@link
 * RouteMembership#driversAt} for the route's driver at that time and now (ADR-114), and {@link
 * PublishedRoutes} for the route's date. Then it creates the {@code RouteRun} through {@code
 * RouteRun.start}, which raises {@code RouteStarted}, with the receipt {@link
 * com.dispatchflow.liveops.application.port.out.RecordedReport.Started} and the audit record; a
 * route that already has a run is refused by the run itself. Like every changing call it writes its
 * audit record, a refused attempt's in its own transaction (NFR-23, ADR-110). After the commit, it
 * tells the open screens through {@link LiveUpdates}.
 *
 * <p>To Be Implemented. Rows: FR-68, FR-70, FR-244, FR-327, BR-28, NFR-65,
 * NFR-130.
 */
public final class StartRoute {

    private final LiveUpdates liveUpdates;
    private final StartRouteChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the open screens
     * @param change the use case's transaction
     */
    public StartRoute(LiveUpdates liveUpdates, StartRouteChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Starts the route. A resend with the same key and body returns having changed nothing.
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
     * @throws NotFound when no plan in force holds the route; the phone's outbox marks the row
     *     refused (ADR-121).
     * @throws IdempotencyKeyReused when the key was used before with another body, or its stored
     *     receipt is another call's: a {@code RecordedReport} case other than {@code Started}
     *     (NFR-130)
     * @throws StaleVersion when another start or first report created the run meanwhile, or a
     *     concurrent send recorded the key first; the web adapter retries the call at once, up to
     *     three times, then answers 503 with {@code Retry-After} (ADR-121), and the phone's outbox
     *     backs off and resends it with the same key (ADR-087)
     */
    public void start(Actor actor, RouteId route, DeviceClock time, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
