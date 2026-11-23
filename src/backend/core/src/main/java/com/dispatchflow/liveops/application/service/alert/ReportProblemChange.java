package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.ReportReceipts;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.liveops.domain.alert.DriverProblem;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link ReportProblem} (FR-95): it checks the acting driver drives the route
 * now through Planning's {@link PublishedRoutes} (BR-02), and raises the {@code Alert} with the
 * receipt {@link com.dispatchflow.liveops.application.port.out.RecordedReport.ProblemReported} and
 * the audit record (ADR-107, NFR-23). A resend with the same key and body gets the stored receipt
 * and raises nothing (BR-28, NFR-65).
 *
 * <p>Built in M8.
 */
@Transactional
public class ReportProblemChange {

    private final AlertRepository alerts;
    private final ReportReceipts receipts;
    private final PublishedRoutes publishedRoutes;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param alerts where alerts are kept
     * @param receipts the phone's idempotency record
     * @param publishedRoutes Planning's routes in force, for the route's plan, date and driver
     * @param clock the server's time
     * @param ids new IDs, for the alert
     * @param auditLog the audit trail
     */
    public ReportProblemChange(
            AlertRepository alerts,
            ReportReceipts receipts,
            PublishedRoutes publishedRoutes,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.alerts = alerts;
        this.receipts = receipts;
        this.publishedRoutes = publishedRoutes;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Raises the alert for the problem, with the receipt and the audit record.
     *
     * <p>Built in M8.
     *
     * @param actor the driver
     * @param route the driver's route
     * @param problem what the problem is
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @return the alert raised, or empty when the call is a resend whose stored answer it returns
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code NotYourRoute} when
     *     the acting driver does not drive the route now (BR-02)
     * @throws NotFound when no plan in force holds the route
     * @throws IdempotencyKeyReused when the key was used before with another body, or its stored
     *     receipt is another call's (NFR-130)
     * @throws StaleVersion when a concurrent send recorded the key first
     */
    public Optional<AlertId> apply(
            Actor actor, RouteId route, DriverProblem problem, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
