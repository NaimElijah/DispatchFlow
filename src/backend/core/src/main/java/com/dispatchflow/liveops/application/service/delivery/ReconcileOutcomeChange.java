package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.events.OutcomeRecorded;
import com.dispatchflow.planning.application.api.ReconcileLateOutcome;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two transactions of {@link ReconcileOutcome} (DL §8, BR-89, BR-130), each call one of its
 * own. {@link #apply} asks Planning's {@link RouteMembership} where the stop is now, and when the
 * current revision disagrees with the route that served it, raises the dispatcher's <em>late
 * report</em> alert from the {@code WhereaboutsFact}, stored with the plan and its date the same
 * {@code MembershipFact} gives, with the audit record (ADR-107, NFR-23). It commits before {@link
 * #reconcile} calls Planning's {@link ReconcileLateOutcome}, so a failure there can't lose the alert.
 *
 * <p>Built in M8.
 */
@Transactional
public class ReconcileOutcomeChange {

    private final RouteMembership membership;
    private final ReconcileLateOutcome reconcileLateOutcome;
    private final AlertRepository alerts;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param membership Planning's answers on where a stop is now
     * @param reconcileLateOutcome Planning's reconciliation of a late outcome
     * @param alerts where alerts are kept
     * @param clock the server's time
     * @param ids new IDs, for the alert
     * @param auditLog the audit trail
     */
    public ReconcileOutcomeChange(
            RouteMembership membership,
            ReconcileLateOutcome reconcileLateOutcome,
            AlertRepository alerts,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.membership = membership;
        this.reconcileLateOutcome = reconcileLateOutcome;
        this.alerts = alerts;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Raises the late-report alert when the current plan disagrees with the outcome, and writes the
     * audit record.
     *
     * <p>Built in M8.
     *
     * @param event the recorded outcome
     * @param call the listener's server step, which the audit record carries
     * @return the alert raised, or empty when the plan agrees with the outcome or the stop's alert
     *     key was taken by an earlier delivery of the event
     * @throws NotFound when no plan holds the stop, so the delivery is retried
     */
    public Optional<AlertId> apply(OutcomeRecorded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }

    /**
     * Asks Planning's {@link ReconcileLateOutcome} to reconcile the plan with the accepted outcome,
     * after {@link #apply} committed. Reconciling a stop already where the fact puts it changes
     * nothing.
     *
     * <p>Built in M8.
     *
     * @param event the recorded outcome
     * @param call the listener's server step, which Planning's call carries
     * @throws NotFound when no plan holds the stop, so the delivery is retried
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished} or
     *     {@code RouteNotInPlan}, which only a bug gives (DL §12)
     * @throws StaleVersion when Planning's plan changed meanwhile
     */
    public void reconcile(OutcomeRecorded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
