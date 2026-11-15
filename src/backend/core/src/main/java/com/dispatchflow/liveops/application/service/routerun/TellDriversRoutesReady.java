package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.events.PlanPublished;

/**
 * Live Operations' listener on {@code PlanPublished}: every driver with a route on the plan is told
 * their route is ready, through the channel of FR-71's route-change alerts (FR-152, FR-324). The
 * drivers come from Planning's {@link PublishedRoutes}. Each driver gets one {@code
 * LiveUpdate.RoutesPublished} through {@link LiveUpdates}, which the web app open on the driver's
 * routes hears (FR-391), and one {@code DriverNotice.RoutesReady} through {@link PushNotifier}, which
 * reaches the phone app in the background (ADR-026, ADR-083).
 *
 * <p>Delivery is at least once (ADR-064), and a repeated update or push only repeats a notice that
 * carries no data (NFR-218), so it records no {@code ProcessedEvents} pair.
 *
 * <p>Built in M5 for the stream, which the driver's web screens hear; the push through {@link
 * PushNotifier} in M10. Rows: FR-152, FR-324, FR-391.
 */
public final class TellDriversRoutesReady {

    private final PushNotifier pushNotifier;
    private final PublishedRoutes publishedRoutes;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the listener's use case.
     *
     * @param pushNotifier the drivers' phones
     * @param publishedRoutes Planning's routes in force, for each route's driver
     * @param liveUpdates the open screens, the drivers' web app among them
     */
    public TellDriversRoutesReady(PushNotifier pushNotifier, PublishedRoutes publishedRoutes, LiveUpdates liveUpdates) {
        this.pushNotifier = pushNotifier;
        this.publishedRoutes = publishedRoutes;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Tells each of the plan's drivers once, however many routes they drive.
     *
     * <p>Built in M5 for the stream; the push in M10.
     *
     * @param event the published plan
     * @param call the listener's server step
     */
    public void on(PlanPublished event, CallContext call) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
