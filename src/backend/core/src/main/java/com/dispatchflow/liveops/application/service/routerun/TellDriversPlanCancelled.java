package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.planning.events.PlanCancelled;

/**
 * Live Operations' listener on {@code PlanCancelled}: each driver with a route on the cancelled plan
 * is alerted (FR-337, ADR-101). A cancelled plan is no longer in force, so the drivers come from
 * Planning's {@link RouteMembership#driversAt} for each route the event names, at the cancellation's
 * time, read in {@link TellDriversPlanCancelledChange}'s read-only transaction. After it, each
 * driver gets one {@code LiveUpdate.PlanCancelled} through {@link LiveUpdates}, which the web app
 * hears (FR-347), and one {@code DriverNotice.PlanCancelled} through {@link PushNotifier}, which
 * reaches the phone app in the background (ADR-026, ADR-083).
 *
 * <p>Delivery is at least once (ADR-064), and a repeated update or push only repeats a notice that
 * carries no data (NFR-218), so it records no {@code ProcessedEvents} pair.
 *
 * <p>Built in M9 for the stream, when plans are first cancelled; the push through {@link
 * PushNotifier} in M10. Rows: FR-337.
 */
public final class TellDriversPlanCancelled {

    private final PushNotifier pushNotifier;
    private final LiveUpdates liveUpdates;
    private final TellDriversPlanCancelledChange change;

    /**
     * Creates the listener's use case.
     *
     * @param pushNotifier the drivers' phones
     * @param liveUpdates the open screens, the drivers' web app among them
     * @param change the read of the plan's drivers
     */
    public TellDriversPlanCancelled(
            PushNotifier pushNotifier, LiveUpdates liveUpdates, TellDriversPlanCancelledChange change) {
        this.pushNotifier = pushNotifier;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Tells each of the plan's drivers once.
     *
     * <p>Built in M9 for the stream; the push in M10.
     *
     * @param event the cancelled plan
     * @param call the listener's server step
     */
    public void on(PlanCancelled event, CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
