package com.dispatchflow.liveops.application.service.delivery.dispatcher;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link CorrectOutcome} (FR-333, FR-374, FR-404): it builds the domain's {@code
 * LaterAttempt} from Planning's {@link RouteMembership#laterStopOf} and that stop's own delivery,
 * reads the door flag from Customers &amp; Orders' {@link StopOrders}, and calls {@code
 * Delivery.correct} with the audit record (ADR-107, NFR-23).
 *
 * <p>Built in M8.
 */
@Transactional
public class CorrectOutcomeChange {

    private final DeliveryRepository deliveries;
    private final RouteMembership membership;
    private final StopOrders stopOrders;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param deliveries where deliveries are kept
     * @param membership Planning's answers on a put-back after a stop
     * @param stopOrders Customers &amp; Orders' door flag of an order
     * @param clock the server's time
     * @param auditLog the audit trail
     */
    public CorrectOutcomeChange(
            DeliveryRepository deliveries,
            RouteMembership membership,
            StopOrders stopOrders,
            Clock clock,
            AuditLog auditLog) {
        this.deliveries = deliveries;
        this.membership = membership;
        this.stopOrders = stopOrders;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Corrects the outcome and writes the audit record.
     *
     * <p>Built in M8.
     *
     * @param actor the dispatcher
     * @param stop the stop whose outcome is corrected
     * @param command the corrected outcome, the reason and the stated time
     * @param expected the delivery's version the dispatcher saw (BR-76)
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     NotALeaveAtDoorOrder}, {@code CorrectionTimeInFuture}, {@code NoOutcomeToCorrect}, {@code
     *     CorrectionChangesNothing} or {@code LaterAttemptHasOutcome} (FR-404), in that order
     * @throws NotFound when nothing is recorded for the stop
     * @throws StaleVersion when the delivery changed since {@code expected}
     */
    public void apply(Actor actor, StopId stop, CorrectOutcome.Command command, Version expected) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
