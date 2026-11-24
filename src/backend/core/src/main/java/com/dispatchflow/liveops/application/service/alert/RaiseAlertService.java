package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.api.RaiseAlert;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.StopId;

/**
 * Live Operations' implementation of {@link RaiseAlert}, which Messaging's listener on {@code
 * RatingReceived} calls (FR-361, DL §2.2). A rated stop was delivered, so its delivery names the
 * route that served it, and Planning's {@link PublishedRoutes} that route's plan and its date; the
 * {@code AlertSubject.LowRating} is keyed on the rating request, so a retried listener raises it
 * once (ADR-107 point 4). {@link RaiseAlertServiceChange} holds the transaction that raises it.
 *
 * <p>After the commit, it tells the open screens of the alert raised through {@link LiveUpdates},
 * and calls {@link PushNotifier#alertStaff}, so a staff phone app in the background learns of it
 * (FR-405, ADR-083).
 *
 * <p>To Be Implemented.
 */
public final class RaiseAlertService implements RaiseAlert {

    private final PushNotifier pushNotifier;
    private final LiveUpdates liveUpdates;
    private final RaiseAlertServiceChange change;

    /**
     * Creates the service.
     *
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param liveUpdates the open screens
     * @param change the service's transaction
     */
    public RaiseAlertService(PushNotifier pushNotifier, LiveUpdates liveUpdates, RaiseAlertServiceChange change) {
        this.pushNotifier = pushNotifier;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public void lowRating(StopId stop, RatingRequestId request, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
