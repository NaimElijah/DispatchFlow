package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.PositionLog;
import com.dispatchflow.liveops.application.port.out.ReportReceipts;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RecordPositions} (FR-31, FR-243, FR-328): it corrects each fix's time
 * (BR-114), builds the {@code TrackingStanding} from Planning's {@link PublishedRoutes} and Fleet's
 * {@link FleetFacts#driverOn}, keeps the fixes {@code RouteRun.acceptsPositionAt} accepts, and
 * appends them with the receipt and the audit record (NFR-285, NFR-23).
 *
 * <p>Built in M8.
 */
@Transactional
public class RecordPositionsChange {

    private final RouteRunRepository runs;
    private final PositionLog positions;
    private final ReportReceipts receipts;
    private final PublishedRoutes publishedRoutes;
    private final FleetFacts fleetFacts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param runs where route runs are kept
     * @param positions where positions are kept
     * @param receipts the phone's idempotency record
     * @param publishedRoutes Planning's routes in force, for the current driver and return time
     * @param fleetFacts Fleet's roster, for the driver's availability and shift
     * @param clock the server's time
     * @param auditLog the audit trail
     */
    public RecordPositionsChange(
            RouteRunRepository runs,
            PositionLog positions,
            ReportReceipts receipts,
            PublishedRoutes publishedRoutes,
            FleetFacts fleetFacts,
            Clock clock,
            AuditLog auditLog) {
        this.runs = runs;
        this.positions = positions;
        this.receipts = receipts;
        this.publishedRoutes = publishedRoutes;
        this.fleetFacts = fleetFacts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Keeps the batch's positions the tracking rule accepts, with the receipt and the audit record.
     *
     * <p>Built in M8.
     *
     * @param actor the sending driver
     * @param route the route the batch was sent for
     * @param fixes the positions, in the order recorded
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @return how many positions it kept; none when the rule accepts none, or the call is a resend
     *     whose stored answer it returns
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     DeviceTimeOutOfRange} for a fix's device instant outside the years 1 to 9999
     * @throws NotFound when no plan in force holds the route
     * @throws IdempotencyKeyReused when the key was used before with another body, or its stored
     *     receipt is another call's (NFR-130)
     * @throws StaleVersion when a concurrent send recorded the key first
     */
    public int apply(Actor actor, RouteId route, List<PositionFix> fixes, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
