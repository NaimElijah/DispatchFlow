package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.SessionContacts;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.ReportReceipts;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.port.out.RouteRunRepository;
import com.dispatchflow.liveops.application.port.out.proof.ProofStore;
import com.dispatchflow.liveops.application.service.DeviceClock;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link ReportStop} (DL §6.2, §8, ADR-087). {@link #apply} reads the report's
 * corrected time, Planning's membership, Access's session contacts and the order's door flag, and
 * changes the stop's {@code Delivery}, with the route's first {@code RouteRun}, the dispatcher's
 * alerts, the receipt and the audit record (BR-110, BR-132 to BR-134, FR-327, NFR-23). {@link
 * #alertRefused} raises a contradicting report's <em>report refused</em> alert in a transaction of
 * its own, after {@link #apply} refused it (BR-90).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class ReportStopChange {

    private final DeliveryRepository deliveries;
    private final RouteRunRepository runs;
    private final AlertRepository alerts;
    private final ReportReceipts receipts;
    private final RouteMembership membership;
    private final PublishedRoutes publishedRoutes;
    private final SessionContacts sessionContacts;
    private final StopOrders stopOrders;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param deliveries where deliveries are kept
     * @param runs where route runs are kept
     * @param alerts where alerts are kept
     * @param receipts the phone's idempotency record
     * @param membership Planning's answers on which route held a stop when
     * @param publishedRoutes Planning's routes in force, for the date a first report's route run is
     *     stored with
     * @param sessionContacts Access's contact times of an account's sessions
     * @param stopOrders Customers &amp; Orders' door flag of an order
     * @param clock the server's time
     * @param ids new IDs, for a held report and the alerts
     * @param auditLog the audit trail
     */
    public ReportStopChange(
            DeliveryRepository deliveries,
            RouteRunRepository runs,
            AlertRepository alerts,
            ReportReceipts receipts,
            RouteMembership membership,
            PublishedRoutes publishedRoutes,
            SessionContacts sessionContacts,
            StopOrders stopOrders,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.deliveries = deliveries;
        this.runs = runs;
        this.alerts = alerts;
        this.receipts = receipts;
        this.membership = membership;
        this.publishedRoutes = publishedRoutes;
        this.sessionContacts = sessionContacts;
        this.stopOrders = stopOrders;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Takes the driver's report, with the receipt and the audit record. A resend with the same key
     * and body gets the stored receipt and changes nothing (BR-28, NFR-65).
     *
     * <p>To Be Implemented.
     *
     * @param actor the reporting driver
     * @param report what the driver reports, at which stop of which route
     * @param time the device's time of the report and its clock when it sent it
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @param storedProof what {@link ProofStore#find} answered for the proof a delivered report
     *     names; empty for an arrival, a failure, a delivered report naming no proof, or a proof the
     *     store doesn't hold
     * @return what the report did, and the alerts it raised
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     DeviceTimeOutOfRange}; for an arrival, {@code ReportAfterCancellation}, {@code
     *     StopNotOnYourRoute}, {@code ArrivalAfterOutcome} or {@code ArrivalAlreadyRecorded}; for a
     *     delivered, {@code ProofRequired}, {@code ProofHasNoFile} (also for a proof another driver
     *     uploaded, or of another kind than stated), {@code NotALeaveAtDoorOrder}, {@code
     *     LeftAtDoorNeedsPhoto}, {@code ReportAfterCancellation}, {@code StopNotOnYourRoute}, {@code
     *     SameOutcomeAlreadyRecorded} or {@code OutcomeAlreadyRecorded}; for a failed, {@code
     *     ReportAfterCancellation}, {@code StopNotOnYourRoute}, {@code SameOutcomeAlreadyRecorded}
     *     or {@code OutcomeAlreadyRecorded}
     * @throws NotFound when no plan holds the stop or the route
     * @throws IdempotencyKeyReused when the key was used before with another body, or its stored
     *     receipt is another call's (NFR-130)
     * @throws StaleVersion when another writer saved the stop's delivery meanwhile
     */
    public Taken apply(
            Actor actor,
            StopReport report,
            DeviceClock time,
            IdempotencyKey key,
            RequestDigest digest,
            Optional<ProofStore.StoredProof> storedProof) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Raises the <em>report refused</em> alert for a contradicting report that {@link #apply}
     * refused, and writes the audit record, so the refusal's rollback doesn't take the alert with
     * it (BR-90).
     *
     * <p>To Be Implemented.
     *
     * @param actor the reporting driver
     * @param report the refused report
     * @param time the device's time of the report and its clock when it sent it
     * @return the alert raised, or empty when an earlier send of the same report raised it
     */
    public Optional<AlertId> alertRefused(Actor actor, StopReport report, DeviceClock time) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * What {@link #apply} did: the answer the phone's outbox gets, and the alerts raised, which the
     * use case pushes after the commit.
     *
     * <p>To Be Implemented.
     *
     * @param receipt what the report did, and whether it is a retry's stored answer
     * @param alertsRaised the dispatcher's alerts the report raised; none for a replayed answer
     */
    public record Taken(ReportReceipt receipt, List<AlertId> alertsRaised) {

        /** Checks both parts are present, and copies the alerts. */
        public Taken {
            Objects.requireNonNull(receipt, "receipt");
            alertsRaised = List.copyOf(alertsRaised);
        }
    }
}
