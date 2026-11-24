package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RaiseAlertService} (FR-361): it reads the route that served the rated
 * stop from its delivery and that route's plan and date from Planning's {@link PublishedRoutes}, and
 * raises the {@code AlertSubject.LowRating} keyed on the rating request, with the audit record
 * (ADR-107 point 4, NFR-23).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class RaiseAlertServiceChange {

    private final AlertRepository alerts;
    private final DeliveryRepository deliveries;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param alerts where alerts are kept
     * @param deliveries where deliveries are kept, for the stop's serving route
     * @param publishedRoutes Planning's routes, for the route's plan
     * @param clock the server's time
     * @param ids new IDs, for the alert
     * @param auditLog the audit trail
     */
    public RaiseAlertServiceChange(
            AlertRepository alerts,
            DeliveryRepository deliveries,
            PublishedRoutes publishedRoutes,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.alerts = alerts;
        this.deliveries = deliveries;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Raises the low-rating alert and writes the audit record.
     *
     * <p>To Be Implemented.
     *
     * @param stop the rated stop
     * @param request the rating request, the alert's key
     * @param call the listener's server step
     * @return the alert raised, or empty when a retried listener finds its key taken
     */
    public Optional<AlertId> apply(StopId stop, RatingRequestId request, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
