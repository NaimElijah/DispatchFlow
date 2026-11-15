package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.planning.events.RouteHandedOver;

/**
 * Live Operations' listener on {@code PlanRevised}: the driver of each route the revision changed
 * gets a change notice, even with the app in the background, and confirms it with <em>Seen</em>
 * (FR-71, FR-97, FR-249). The drivers come from Planning's {@link PublishedRoutes}. Each changed
 * route's driver gets one {@code LiveUpdate.RouteRevised} through {@link LiveUpdates}, which the web
 * app open on the route hears and reads the route again from (FR-347, FR-391), and which the
 * dispatcher's unconfirmed list follows (FR-250); and one {@code DriverNotice.RouteChanged} through
 * {@link PushNotifier}, which reaches the phone app in the background (ADR-026, ADR-083).
 *
 * <p>A route handed over has a new driver in the current revision. The driver it left is in no
 * route of that revision, so {@link #on(RouteHandedOver, CallContext)} tells them from Planning's
 * {@code RouteHandedOver}, which names both drivers (FR-71), with the same update and push.
 *
 * <p>Delivery is at least once (ADR-064), and a repeated notice carries no data (NFR-218), so it
 * records no {@code ProcessedEvents} pair.
 *
 * <p>Built in M8 and M9, method by method, for the stream: M8 raises the first revision after
 * publishing, which the driver's web screens and the unconfirmed list hear, and M9 the hand-over.
 * The push through {@link PushNotifier} in M10. Rows: FR-71, FR-97, FR-249, FR-347, FR-391.
 */
public final class TellDriversOfRevision {

    private final PushNotifier pushNotifier;
    private final PublishedRoutes publishedRoutes;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the listener's use case.
     *
     * @param pushNotifier the drivers' phones
     * @param publishedRoutes Planning's routes in force, for each changed route's driver
     * @param liveUpdates the open screens, the drivers' web app among them
     */
    public TellDriversOfRevision(PushNotifier pushNotifier, PublishedRoutes publishedRoutes, LiveUpdates liveUpdates) {
        this.pushNotifier = pushNotifier;
        this.publishedRoutes = publishedRoutes;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Tells the driver of each changed route.
     *
     * <p>Built in M8 for the stream; the push in M10.
     *
     * @param event the revision
     * @param call the listener's server step
     */
    public void on(PlanRevised event, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }

    /**
     * Tells the driver a handed-over route left that it is no longer theirs (FR-71).
     *
     * <p>Built in M9 for the stream; the push in M10.
     *
     * @param event the hand-over, naming the driver the route left
     * @param call the listener's server step
     */
    public void on(RouteHandedOver event, CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
