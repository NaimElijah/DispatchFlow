package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.api.RaiseAlert;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * Live Operations' implementation of {@link RaiseAlert}, which Messaging's listener on {@code
 * RatingReceived} calls (FR-361, DL §2.2). A rated stop was delivered, so its delivery names the
 * route that served it, and Planning's {@link PublishedRoutes} that route's plan; the {@code
 * AlertSubject.LowRating} is keyed on the rating request, so a retried listener raises it once
 * (ADR-107 point 4).
 *
 * <p>For each alert it raises, it calls {@link PushNotifier#alertStaff} after the commit, so a
 * staff phone app in the background learns of it (FR-405, ADR-083).
 *
 * <p>Built in M12.
 */
public final class RaiseAlertService implements RaiseAlert {

    private final AlertRepository alerts;
    private final PushNotifier pushNotifier;
    private final DeliveryRepository deliveries;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the service.
     *
     * @param alerts where alerts are kept
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param deliveries where deliveries are kept, for the stop's serving route
     * @param publishedRoutes Planning's routes, for the route's plan
     * @param clock the server's time
     * @param ids new IDs, for the alert
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public RaiseAlertService(
            AlertRepository alerts,
            PushNotifier pushNotifier,
            DeliveryRepository deliveries,
            PublishedRoutes publishedRoutes,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.alerts = alerts;
        this.pushNotifier = pushNotifier;
        this.deliveries = deliveries;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M12.
     */
    @Override
    public void lowRating(StopId stop, RatingRequestId request, int rating, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
