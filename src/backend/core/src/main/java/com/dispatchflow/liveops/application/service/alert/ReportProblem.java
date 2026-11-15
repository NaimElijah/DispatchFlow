package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.application.port.out.ReportReceipts;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.liveops.domain.alert.DriverProblem;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A driver reports a problem from either app (breakdown, running late, vehicle full), and the
 * dispatcher is alerted (FR-95). Planning's {@link PublishedRoutes} gives the route's plan, its date
 * and its current driver, who alone may report on it (BR-02). One transaction raises the {@code
 * Alert}, keyed on its own ID so every report alerts (ADR-107), with the receipt {@link
 * com.dispatchflow.liveops.application.port.out.RecordedReport.ProblemReported} and the audit record.
 * Like every changing call it writes its audit record, a refused attempt's in its own transaction
 * (NFR-23, ADR-110).
 *
 * <p>The phone's outbox sends the report with a request key, as it sends a stop's report (ADR-087).
 * The alert's own key can't tell a resend from a second report, so the receipt does: a resend with
 * the same key gets the stored answer and raises no second alert (BR-28, NFR-65), and a key reused
 * with another body is refused (NFR-130).
 *
 * <p>For each alert it raises, it calls {@link PushNotifier#alertStaff} after the commit, so a
 * staff phone app in the background learns of it (FR-405, ADR-083).
 *
 * <p>Built in M8; the push through {@link PushNotifier} in M10. Rows: FR-95, BR-28, NFR-65,
 * NFR-130.
 */
public final class ReportProblem {

    private final AlertRepository alerts;
    private final ReportReceipts receipts;
    private final PushNotifier pushNotifier;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param alerts where alerts are kept
     * @param receipts the phone's idempotency record
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param publishedRoutes Planning's routes in force, for the route's plan, date and driver
     * @param clock the server's time
     * @param ids new IDs, for the alert
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public ReportProblem(
            AlertRepository alerts,
            ReportReceipts receipts,
            PushNotifier pushNotifier,
            PublishedRoutes publishedRoutes,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.alerts = alerts;
        this.receipts = receipts;
        this.pushNotifier = pushNotifier;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Raises the dispatcher's alert for the problem. A resend with the same key and body returns
     * having raised nothing.
     *
     * <p>Built in M8; the push in M10.
     *
     * @param actor the driver
     * @param route the driver's route
     * @param problem what the problem is
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code NotYourRoute} when
     *     the acting driver does not drive the route now (BR-02)
     * @throws NotFound when no plan in force holds the route
     * @throws IdempotencyKeyReused when the key was used before with another body (NFR-130)
     * @throws StaleVersion when a concurrent send recorded the key first; the outbox retries with
     *     the same key and gets the stored answer
     */
    public void report(Actor actor, RouteId route, DriverProblem problem, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
