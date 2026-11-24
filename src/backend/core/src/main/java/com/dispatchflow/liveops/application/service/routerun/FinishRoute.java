package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.service.DeviceClock;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.liveops.application.service.RouteNotStarted;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A driver finishes their route, perhaps with stops pending after a warning, and the dispatcher is
 * alerted when stops are (FR-68, FR-329, ADR-107). The phone's outbox may send the finish offline
 * (FR-70), with a request key, as it sends a report, so a resend gets the stored answer and changes
 * nothing (BR-28, NFR-65, ADR-087), and a key reused with another body is refused (NFR-130).
 *
 * <p>{@link FinishRouteChange#apply} holds the transaction, and reads first: the finish's {@code
 * CorrectedTime} (BR-114), Planning's {@link RouteMembership#driversAt} for the route's drivers then
 * and now (ADR-114), and {@link PublishedRoutes} for the route's stops in order, whose deliveries
 * tell which have no outcome. A route with no run is refused with {@link RouteNotStarted} (ADR-107).
 * Then it calls {@code RouteRun.finish}, with the <em>route finished with stops pending</em> alert
 * when any are, the receipt {@link com.dispatchflow.liveops.application.port.out.RecordedReport.Finished}
 * and the audit record. A late report after the finish is still accepted and changes no run
 * (ADR-107). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>The alert is stored with its plan's date from {@link PublishedRoutes}. After the commit, it
 * tells the open screens through {@link LiveUpdates}, and for each alert raised calls {@link
 * PushNotifier#alertStaff}, so a staff phone app in the background learns of it (FR-405, ADR-083).
 *
 * <p>To Be Implemented. It comes with the request key and the pending-stops alert through {@link
 * AlertRepository}, and the push through {@link PushNotifier}. Rows: FR-68, FR-70, FR-329, BR-28,
 * NFR-65, NFR-130, ADR-107.
 */
public final class FinishRoute {

    private final PushNotifier pushNotifier;
    private final LiveUpdates liveUpdates;
    private final FinishRouteChange change;

    /**
     * Creates the use case.
     *
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param liveUpdates the open screens
     * @param change the use case's transaction
     */
    public FinishRoute(PushNotifier pushNotifier, LiveUpdates liveUpdates, FinishRouteChange change) {
        this.pushNotifier = pushNotifier;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Finishes the route. A resend with the same key and body returns having changed nothing.
     *
     * <p>To Be Implemented.
     *
     * @param actor the driver
     * @param route the route
     * @param time the device's time of the finish and its clock when it sent it
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     DeviceTimeOutOfRange}; {@code NotYourRoute} when the driver is not both the route's
     *     driver at that time and now (BR-02, ADR-114); else {@code RouteAlreadyFinished}
     * @throws NotFound when no plan in force holds the route; the phone's outbox marks the row
     *     refused (ADR-121).
     * @throws RouteNotStarted when the route has no run (ADR-107)
     * @throws IdempotencyKeyReused when the key was used before with another body, or its stored
     *     receipt is another call's: a {@code RecordedReport} case other than {@code Finished}
     *     (NFR-130)
     * @throws StaleVersion when another writer saved the run meanwhile, or a concurrent send
     *     recorded the key first; the web adapter retries the call at once, up to three times, then
     *     answers 503 with {@code Retry-After} (ADR-121), and the phone's outbox backs off and
     *     resends it with the same key (ADR-087)
     */
    public void finish(Actor actor, RouteId route, DeviceClock time, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
