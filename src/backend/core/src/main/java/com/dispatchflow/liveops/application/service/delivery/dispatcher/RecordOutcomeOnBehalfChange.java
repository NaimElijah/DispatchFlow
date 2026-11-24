package com.dispatchflow.liveops.application.service.delivery.dispatcher;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.domain.outcome.DispatcherOutcome;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RecordOutcomeOnBehalf} (FR-73, FR-257, FR-368, BR-134): it reads the
 * route's stop, order, attempt and plan from Planning's {@link PublishedRoutes} and the door flag
 * from Customers &amp; Orders' {@link StopOrders}, and calls {@code Delivery.recordOnBehalf},
 * creating the delivery on its first fact, with an alert per overtaken held report and the audit
 * record (ADR-107, NFR-23).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class RecordOutcomeOnBehalfChange {

    private final DeliveryRepository deliveries;
    private final AlertRepository alerts;
    private final PublishedRoutes publishedRoutes;
    private final StopOrders stopOrders;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param deliveries where deliveries are kept
     * @param alerts where alerts are kept
     * @param publishedRoutes Planning's routes in force
     * @param stopOrders Customers &amp; Orders' door flag of an order
     * @param clock the server's time, which the outcome counts from
     * @param ids new IDs, for the alerts
     * @param auditLog the audit trail
     */
    public RecordOutcomeOnBehalfChange(
            DeliveryRepository deliveries,
            AlertRepository alerts,
            PublishedRoutes publishedRoutes,
            StopOrders stopOrders,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.deliveries = deliveries;
        this.alerts = alerts;
        this.publishedRoutes = publishedRoutes;
        this.stopOrders = stopOrders;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Records the outcome, counting from now, and writes the audit record.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param stop the stop
     * @param route the route whose driver it is recorded for
     * @param outcome what the dispatcher records; a failure carries its reason (FR-73)
     * @return the alerts raised, one per overtaken held report; none when no report was held
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     NotALeaveAtDoorOrder}, then {@code SameOutcomeAlreadyRecorded} or {@code
     *     OutcomeAlreadyRecorded} (BR-18)
     * @throws NotFound when no plan in force holds the route, or the route doesn't hold the stop
     * @throws StaleVersion when another writer saved the stop's delivery meanwhile
     */
    public List<AlertId> apply(Actor actor, StopId stop, RouteId route, DispatcherOutcome outcome) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
