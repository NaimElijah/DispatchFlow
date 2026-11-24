package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.events.OutcomeRecorded;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The alert's transaction of {@link ReconcileOutcome} (DL §8, BR-89, BR-130). {@link #apply} asks
 * Planning's {@link RouteMembership} where the stop is now, and when the current revision disagrees
 * with the route that served it, raises the dispatcher's <em>late report</em> alert from the {@code
 * WhereaboutsFact}, stored with the plan and its date the same {@code MembershipFact} gives, with
 * the audit record (ADR-107, NFR-23). It commits before the use case calls Planning's {@code
 * ReconcileLateOutcome}, a use case of its own that opens its own transaction (ADR-129), so a
 * failure there can't lose the alert.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class ReconcileOutcomeChange {

    private final RouteMembership membership;
    private final AlertRepository alerts;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param membership Planning's answers on where a stop is now
     * @param alerts where alerts are kept
     * @param clock the server's time
     * @param ids new IDs, for the alert
     * @param auditLog the audit trail
     */
    public ReconcileOutcomeChange(
            RouteMembership membership, AlertRepository alerts, Clock clock, IdGenerator ids, AuditLog auditLog) {
        this.membership = membership;
        this.alerts = alerts;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Raises the late-report alert when the current plan disagrees with the outcome, and writes the
     * audit record.
     *
     * <p>To Be Implemented.
     *
     * @param event the recorded outcome
     * @param call the listener's server step, which the audit record carries
     * @return the alert raised, or empty when the plan agrees with the outcome or the stop's alert
     *     key was taken by an earlier delivery of the event
     * @throws NotFound when no plan holds the stop, so the delivery is retried
     */
    public Optional<AlertId> apply(OutcomeRecorded event, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
