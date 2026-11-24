package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.SessionContacts;
import com.dispatchflow.liveops.application.port.out.AlertRepository;
import com.dispatchflow.liveops.application.port.out.IdempotencyKey;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.application.port.out.RequestDigest;
import com.dispatchflow.liveops.application.port.out.proof.ProofStore;
import com.dispatchflow.liveops.application.service.DeviceClock;
import com.dispatchflow.liveops.application.service.IdempotencyKeyReused;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A driver reports a stop: arrived, delivered or failed (DL §6.2, §8, ADR-087). The phone's outbox
 * sends each report with a request key, so a retry gets the stored answer (BR-28, NFR-65), and a
 * key reused with another body is refused (NFR-130).
 *
 * <p>Before the transaction, for a <em>delivered</em> report, {@link ProofStore#find} reads what
 * the store knows of the proof it names, on disk; {@link ReportStopChange#apply} takes that answer.
 *
 * <p>{@link ReportStopChange#apply} holds the transaction, and reads first (TS §6.2): the report's {@code
 * CorrectedTime} from the {@link DeviceClock} and the server's clock (BR-44, BR-110, BR-114);
 * Planning's {@link RouteMembership#membershipAt} for the stop, the report's route and that
 * corrected time, whose {@code EntitlementFact} becomes the domain's {@code Entitlement} and whose
 * {@code WhereaboutsFact} becomes its {@code StopWhereabouts}; Access's {@link
 * SessionContacts#contactsOf} for the reporting account over the corrected time's {@code
 * ContactWindow}, which joins those whereabouts in a {@code HoldCheck} (DL §8, BR-132, BR-133,
 * ADR-114); and Customers &amp; Orders' {@link StopOrders}, asked for that one order, for its door
 * flag, as a {@code LeaveAtDoor} (FR-366). A first fact's {@code Delivery.firstFact} takes the
 * stop's order and attempt from the membership's {@code EntitlementFact.OnRoute}, the revision in
 * force at the corrected time, so a late report on a stop the plan has since taken off still
 * creates it (BR-110). The stop's plan and its date, which each alert is stored with, come from the
 * same {@code MembershipFact}, read from the stored revisions, so an alert on a plan cancelled since
 * has both (FR-336). A first report's {@code RouteRun} is stored with its route's date from
 * Planning's {@link PublishedRoutes}.
 *
 * <p>Then it changes the stop's {@code Delivery} through {@code arrive}, {@code deliver} or {@code
 * fail}, creating it on its first fact. In the same transaction it creates the route's {@code
 * RouteRun} on the route's first report (FR-327), unless the report is held or the route was handed
 * away from its sender (ADR-114), the dispatcher's alerts for a held report, a recorded
 * <em>failed</em> (FR-102) and each held report it overtook (BR-134), the receipt, and the audit
 * record. A contradicting report is refused, and its <em>report refused</em> alert raised in a
 * transaction of its own, {@link ReportStopChange#alertRefused}, which the use case calls after the
 * refusal and before it rethrows it (BR-90); a repeated one raises none (BR-88, ADR-107). The
 * screens are told through {@link LiveUpdates} after the commit, and for each alert raised it calls
 * {@link PushNotifier#alertStaff} after the commit, so a staff phone app in the background learns
 * of it (FR-405, ADR-083). Like every changing call it writes its audit record, a refused attempt's
 * in its own transaction (NFR-23, ADR-110).
 *
 * <p>The proof a <em>delivered</em> report names must be stored, uploaded by the acting driver, and
 * of the kind the report states (FR-367, FR-368, BR-02). A proof that fails any of the three is
 * refused as {@code ProofHasNoFile}, which says only that the report names no file of the driver's
 * own, so the driver learns nothing of another driver's upload. The domain has no error of its own
 * for the uploader or the kind, so the use case raises that one.
 *
 * <p>To Be Implemented. It comes online with a signature as the proof, with the request key, the
 * hold, photos and the alerts through {@link AlertRepository}, and with the push through {@link
 * PushNotifier}. Rows: FR-29, FR-30, FR-70, FR-102, FR-242, FR-245 to FR-248, FR-298, FR-365 to
 * FR-368, FR-405, BR-02, BR-27, BR-28, BR-88 to BR-90, BR-110, BR-114, BR-132 to BR-136, NFR-65,
 * NFR-130.
 */
public final class ReportStop {

    private final ProofStore proofs;
    private final PushNotifier pushNotifier;
    private final LiveUpdates liveUpdates;
    private final ReportStopChange change;

    /**
     * Creates the use case.
     *
     * @param proofs where uploaded proofs are kept, to check a proof names the driver's own
     *     uploaded file of the stated kind
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param liveUpdates the open screens
     * @param change the use case's transactions
     */
    public ReportStop(ProofStore proofs, PushNotifier pushNotifier, LiveUpdates liveUpdates, ReportStopChange change) {
        this.proofs = proofs;
        this.pushNotifier = pushNotifier;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Takes a driver's report. The acting driver is the report's sender; a driver reports only a
     * stop that was on their route at the report's corrected time (BR-02, BR-110).
     *
     * <p>To Be Implemented.
     *
     * @param actor the reporting driver
     * @param report what the driver reports, at which stop of which route
     * @param time the device's time of the report and its clock when it sent it
     * @param key the request key, the outbox row's ID
     * @param digest the request body's hash
     * @return what the report did, and whether it is a retry's stored answer
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     DeviceTimeOutOfRange} for a device instant outside the years 1 to 9999; for an arrival,
     *     {@code ReportAfterCancellation}, {@code StopNotOnYourRoute}, {@code ArrivalAfterOutcome}
     *     or {@code ArrivalAlreadyRecorded}; for a delivered, {@code ProofRequired}, {@code
     *     ProofHasNoFile} (also for a proof another driver uploaded, or of another kind than
     *     stated), {@code NotALeaveAtDoorOrder}, {@code LeftAtDoorNeedsPhoto}, {@code
     *     ReportAfterCancellation}, {@code StopNotOnYourRoute}, {@code SameOutcomeAlreadyRecorded}
     *     or {@code OutcomeAlreadyRecorded}; for a failed, {@code ReportAfterCancellation}, {@code
     *     StopNotOnYourRoute}, {@code SameOutcomeAlreadyRecorded} or {@code OutcomeAlreadyRecorded}
     * @throws NotFound when no plan holds the stop or the route; the phone's outbox marks the row
     *     refused (ADR-121).
     * @throws IdempotencyKeyReused when the key was used before with another body, or its stored
     *     receipt is another call's: a {@code RecordedReport} case other than {@code Recorded} or
     *     {@code Held} (NFR-130)
     * @throws StaleVersion when another writer saved the stop's delivery meanwhile; the web adapter
     *     retries the call at once, up to three times, then answers 503 with {@code Retry-After}
     *     (ADR-121), and the phone's outbox backs off and resends it with the same key (ADR-087)
     */
    public ReportReceipt report(
            Actor actor, StopReport report, DeviceClock time, IdempotencyKey key, RequestDigest digest) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
