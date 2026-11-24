package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;

/**
 * A driver's phone, or the web app open on the route, sends a batch of positions every 30 seconds
 * while the route is in progress (FR-31, FR-243). Each fix's time is corrected for the device's
 * clock (BR-114), and {@code RouteRun.acceptsPositionAt} keeps only those inside the run's tracking
 * window from the route's current driver while available (FR-328, NFR-30). The {@code
 * TrackingStanding} it is judged by comes from Planning's {@link PublishedRoutes} (the current
 * driver, and a confirmed return, FR-394) and Fleet's {@link FleetFacts#driverOn} (the availability
 * and the shift end). {@link RecordPositionsChange} holds the one transaction, which appends the
 * kept positions with the receipt and the audit record, so a retried batch is stored once
 * (NFR-285). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110). After the commit, when it kept any position, it tells the open
 * screens through {@link LiveUpdates}.
 *
 * <p>To Be Implemented. Rows: FR-31, FR-243, FR-328, NFR-30, NFR-285.
 */
public final class RecordPositions {

    private final LiveUpdates liveUpdates;
    private final RecordPositionsChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the open screens
     * @param change the use case's transaction
     */
    public RecordPositions(LiveUpdates liveUpdates, RecordPositionsChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Keeps the batch's positions the tracking rule accepts, and drops the others silently, so a
     * phone that keeps sending after a hand-over or past the cut-off is not refused for the batch.
     *
     * <p>To Be Implemented.
     *
     * @param actor the sending driver
     * @param route the route the batch was sent for
     * @param fixes the positions, in the order recorded
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     DeviceTimeOutOfRange} for a fix's device instant outside the years 1 to 9999
     * @throws NotFound when no plan in force holds the route; the phone's outbox marks the row
     *     refused (ADR-121).
     * @throws IdempotencyKeyReused when the key was used before with another body, or its stored
     *     receipt is another call's: a {@code RecordedReport} case other than {@code
     *     PositionsTaken} (NFR-130)
     * @throws StaleVersion when a concurrent send recorded the key first; the web adapter retries
     *     the call at once, up to three times, then answers 503 with {@code Retry-After} (ADR-121),
     *     and the phone's outbox backs off and resends it with the same key (ADR-087). A retry or a
     *     resend gets the stored answer
     */
    public void record(Actor actor, RouteId route, List<PositionFix> fixes, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
