package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.events.PlanRevised;

/**
 * Live Operations' listener on {@code PlanRevised}: the driver of each route the revision changed
 * gets a change notice, even with the app in the background, and confirms it with <em>Seen</em>
 * (FR-71, FR-97, FR-249). The drivers come from Planning's {@link PublishedRoutes}; the screens are
 * told the routes changed, so the unconfirmed list follows (FR-250).
 *
 * <p>A route handed over has a new driver in the current revision; the driver it left is not in the
 * current revision, so Planning's API has to name that driver for them to be told too.
 *
 * <p>Delivery is at least once (ADR-064), and a repeated notice carries no data (NFR-218), so it
 * records no {@code ProcessedEvents} pair.
 *
 * <p>Built in M8. Rows: FR-71, FR-97, FR-249.
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
     * @param liveUpdates the open screens
     */
    public TellDriversOfRevision(PushNotifier pushNotifier, PublishedRoutes publishedRoutes, LiveUpdates liveUpdates) {
        this.pushNotifier = pushNotifier;
        this.publishedRoutes = publishedRoutes;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Tells the driver of each changed route.
     *
     * <p>Built in M8.
     *
     * @param event the revision
     * @param call the listener's server step
     */
    public void on(PlanRevised event, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
