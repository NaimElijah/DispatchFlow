package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.events.OutcomeRecorded;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.ReconcileLateOutcome;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Live Operations' own listener on {@code OutcomeRecorded}: an accepted fact stands, so the plan
 * follows it (DL §8 step 2, BR-89, BR-130, ADR-101). It asks Planning's {@link RouteMembership}
 * where the stop is now; when the current revision disagrees with the route that served it, it calls
 * Planning's {@link ReconcileLateOutcome}, then raises the dispatcher's <em>late report</em> alert
 * from the answer, in a transaction of its own.
 *
 * <p>Delivery is at least once (ADR-064). Both steps are idempotent: reconciling a stop already
 * where the fact puts it changes nothing, and the alert's dedupe key stores it once (ADR-107), so it
 * records no {@code ProcessedEvents} pair.
 *
 * <p>For each alert it raises, it calls {@link PushNotifier#alertStaff} after the commit, so a
 * staff phone app in the background learns of it (FR-405, ADR-083).
 *
 * <p>Built in M8. Rows: DL §8, FR-157, BR-89, BR-130.
 */
public final class ReconcileOutcome {

    private final RouteMembership membership;
    private final ReconcileLateOutcome reconcileLateOutcome;
    private final PublishedRoutes publishedRoutes;
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
     * @param publishedRoutes Planning's routes in force, for the alert's plan
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
            PublishedRoutes publishedRoutes,
            AlertRepository alerts,
            PushNotifier pushNotifier,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.membership = membership;
        this.reconcileLateOutcome = reconcileLateOutcome;
        this.publishedRoutes = publishedRoutes;
        this.alerts = alerts;
        this.pushNotifier = pushNotifier;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Reconciles the plan with an accepted outcome, and alerts the dispatcher when it moved a stop.
     *
     * <p>Built in M8.
     *
     * @param event the recorded outcome
     * @param call the listener's server step, which Planning's call and the audit record carry
     * @throws NotFound when no plan holds the stop, so the delivery is retried
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished} or
     *     {@code RouteNotInPlan}, which only a bug gives, so the delivery is retried and then alerts
     *     the team (DL §12)
     * @throws StaleVersion when Planning's plan changed meanwhile; the listener adapter retries the
     *     event at once, up to three times (DL §11)
     */
    public void on(OutcomeRecorded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
