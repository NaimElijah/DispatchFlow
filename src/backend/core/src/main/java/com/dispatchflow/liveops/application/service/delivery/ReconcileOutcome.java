package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.events.OutcomeRecorded;
import com.dispatchflow.planning.application.api.ReconcileLateOutcome;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Live Operations' own listener on {@code OutcomeRecorded}: an accepted fact stands, so the plan
 * follows it (DL §8 step 2, BR-89, BR-130, ADR-101). It asks Planning's {@link RouteMembership}
 * where the stop is now. When the current revision disagrees with the route that served it, it
 * raises the dispatcher's <em>late report</em> alert first, from the {@code WhereaboutsFact}, in a
 * transaction of its own, stored with the plan and its date that the same {@code MembershipFact}
 * gives from the stored revisions, so an alert on a plan cancelled since has both: an {@code
 * OnAnotherRoute} names the route the stop leaves, and a dropped or cancelled stop names none. Then
 * it calls Planning's {@link ReconcileLateOutcome} (ADR-121).
 *
 * <p>The alert comes first so that a failure between the two steps can't lose it (BR-89). Raised
 * after Planning's commit, a failure before the alert's own commit would leave the plan reconciled,
 * and the retried event would find the stop where the fact puts it and raise nothing. Raised first,
 * a retry finds the plan still disagreeing and reconciles it, and the alert's dedupe key, one per
 * stop, makes the repeated alert harmless.
 *
 * <p>Delivery is at least once (ADR-064). Both steps are idempotent: the alert's dedupe key stores
 * it once (ADR-107), and reconciling a stop already where the fact puts it changes nothing, so it
 * records no {@code ProcessedEvents} pair.
 *
 * <p>For each alert it raises, it calls {@link PushNotifier#alertStaff} after the commit, so a
 * staff phone app in the background learns of it (FR-405, ADR-083).
 *
 * <p>Built in M8; the push through {@link PushNotifier} in M10. Rows: DL §8, FR-157, BR-89, BR-130.
 */
public final class ReconcileOutcome {

    private final RouteMembership membership;
    private final ReconcileLateOutcome reconcileLateOutcome;
    private final AlertRepository alerts;
    private final PushNotifier pushNotifier;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the listener's use case.
     *
     * @param membership Planning's answers on where a stop is now
     * @param reconcileLateOutcome Planning's reconciliation of a late outcome
     * @param alerts where alerts are kept
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param clock the server's time
     * @param ids new IDs, for the alert
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public ReconcileOutcome(
            RouteMembership membership,
            ReconcileLateOutcome reconcileLateOutcome,
            AlertRepository alerts,
            PushNotifier pushNotifier,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.membership = membership;
        this.reconcileLateOutcome = reconcileLateOutcome;
        this.alerts = alerts;
        this.pushNotifier = pushNotifier;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Alerts the dispatcher when the current plan disagrees with an accepted outcome, and then
     * reconciles the plan with it.
     *
     * <p>Built in M8; the push in M10.
     *
     * @param event the recorded outcome
     * @param call the listener's server step, which Planning's call and the audit record carry
     * @throws NotFound when no plan holds the stop, so the delivery is retried
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished} or
     *     {@code RouteNotInPlan}, which only a bug gives, so the delivery is retried and then
     *     alerts the team (DL §12)
     * @throws StaleVersion when Planning's plan changed meanwhile; the listener adapter retries the
     *     event at once, up to three times (DL §11)
     */
    public void on(OutcomeRecorded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
