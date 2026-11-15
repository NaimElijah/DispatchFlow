package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.events.PlanPublished;

/**
 * Live Operations' listener on {@code PlanPublished}: every driver with a route on the plan is told
 * their route is ready, through the channel of FR-71's route-change alerts (FR-152, FR-324). The
 * drivers come from Planning's {@link PublishedRoutes}.
 *
 * <p>Delivery is at least once (ADR-064), and a repeated push only repeats a notice that carries no
 * data (NFR-218), so it records no {@code ProcessedEvents} pair.
 *
 * <p>Built in M10. Rows: FR-152.
 */
public final class TellDriversRoutesReady {

    private final PushNotifier pushNotifier;
    private final PublishedRoutes publishedRoutes;

    /**
     * Creates the listener's use case.
     *
     * @param pushNotifier the drivers' phones
     * @param publishedRoutes Planning's routes in force, for each route's driver
     */
    public TellDriversRoutesReady(PushNotifier pushNotifier, PublishedRoutes publishedRoutes) {
        this.pushNotifier = pushNotifier;
        this.publishedRoutes = publishedRoutes;
    }

    /**
     * Tells each of the plan's drivers once, however many routes they drive.
     *
     * <p>Built in M10.
     *
     * @param event the published plan
     * @param call the listener's server step
     */
    public void on(PlanPublished event, CallContext call) {
        throw new UnsupportedOperationException("Built in M10");
    }
}
