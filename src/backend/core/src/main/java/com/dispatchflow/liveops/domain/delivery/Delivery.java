package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.CancellationKind;
import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.DriverReport;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.LiveOpsError.ArrivalAfterOutcome;
import com.dispatchflow.liveops.domain.LiveOpsError.ArrivalAlreadyRecorded;
import com.dispatchflow.liveops.domain.LiveOpsError.CorrectionChangesNothing;
import com.dispatchflow.liveops.domain.LiveOpsError.CorrectionTimeInFuture;
import com.dispatchflow.liveops.domain.LiveOpsError.LaterAttemptHasOutcome;
import com.dispatchflow.liveops.domain.LiveOpsError.LeftAtDoorNeedsPhoto;
import com.dispatchflow.liveops.domain.LiveOpsError.NoOutcomeToCorrect;
import com.dispatchflow.liveops.domain.LiveOpsError.NotALeaveAtDoorOrder;
import com.dispatchflow.liveops.domain.LiveOpsError.OutcomeAlreadyRecorded;
import com.dispatchflow.liveops.domain.LiveOpsError.ProofHasNoFile;
import com.dispatchflow.liveops.domain.LiveOpsError.ProofRequired;
import com.dispatchflow.liveops.domain.LiveOpsError.ReportAfterCancellation;
import com.dispatchflow.liveops.domain.LiveOpsError.ReportNotHeld;
import com.dispatchflow.liveops.domain.LiveOpsError.SameOutcomeAlreadyRecorded;
import com.dispatchflow.liveops.domain.LiveOpsError.StopNotOnYourRoute;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.domain.delivery.history.ArrivalRecord;
import com.dispatchflow.liveops.domain.delivery.history.CorrectionReason;
import com.dispatchflow.liveops.domain.delivery.history.HeldReport;
import com.dispatchflow.liveops.domain.delivery.history.HeldState;
import com.dispatchflow.liveops.domain.delivery.history.Origin;
import com.dispatchflow.liveops.domain.delivery.history.OutcomeRecord;
import com.dispatchflow.liveops.domain.outcome.DispatcherOutcome;
import com.dispatchflow.liveops.domain.outcome.FailureReason;
import com.dispatchflow.liveops.domain.outcome.Outcome;
import com.dispatchflow.liveops.domain.proof.Proof;
import com.dispatchflow.liveops.domain.proof.ProofKind;
import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.liveops.events.OutcomeRecorded;
import com.dispatchflow.liveops.events.PutBackSuperseded;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The facts recorded for one stop (DL §4.5, §9.3), keyed by its {@link StopId} and created by its
 * first fact. It holds the driver's arrival, the outcome with every correction beside the original
 * (FR-333), and the reports held for a dispatcher (BR-132). Its state is derived from those
 * records, never stored, so the two can't disagree.
 *
 * <p>The first outcome wins (BR-18): a report that repeats it is refused silently, and one that
 * contradicts it is refused and shown to the dispatcher (BR-88, BR-90). A late outcome that would
 * take the stop off another driver's route or override a cancellation is held when the reporting
 * account was in contact meanwhile (BR-132), and an outcome on the stop's current route overtakes
 * a held report (BR-134). Every method checks all its rules before it changes anything. Its
 * records hold reasons, proofs and notes, so it is personal data.
 *
 * <p>It is mapped for JPA (ADR-126): its key is its stop's UUID (ADR-127 decision 5), its arrival
 * nullable columns, its outcome records an ordered element collection of {@link
 * StoredOutcomeRecord}, and its held reports {@link StoredHeldReport} child entities in the order
 * they were held. Every command changes those in place and ends by checking the invariants at rest
 * that the load check runs.
 */
@Entity
@Table(schema = "liveops", name = "delivery")
public class Delivery extends AggregateRoot implements PersonalData {

    @Id
    private UUID id;

    // "order" alone is an SQL keyword.
    @Column(name = "order_id")
    private OrderId order;

    private Attempt attempt;

    private @Nullable AccountId arrivalDriver;

    private @Nullable RouteId arrivalRoute;

    @Embedded
    @AttributeOverride(name = "deviceTime", column = @Column(name = "arrival_device_time"))
    @AttributeOverride(name = "deviceClockAtSend", column = @Column(name = "arrival_device_clock_at_send"))
    @AttributeOverride(name = "receivedAt", column = @Column(name = "arrival_received_at"))
    private @Nullable CorrectedTime arrivalTime;

    @ElementCollection
    @CollectionTable(schema = "liveops", name = "delivery_outcome", joinColumns = @JoinColumn(name = "delivery_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<StoredOutcomeRecord> outcomes = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "delivery_id", nullable = false)
    @OrderColumn(name = "position", nullable = false)
    private List<StoredHeldReport> heldReports = new ArrayList<>();

    /** For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored}. */
    protected Delivery() {}

    private Delivery(StopId stop, OrderId order, Attempt attempt) {
        this.id = stop.value();
        this.order = order;
        this.attempt = attempt;
    }

    /**
     * A stop's delivery before its first fact, which the caller records next (DL §3.5). Saved only
     * once a command records something; with nothing recorded it is *pending*, as no delivery is.
     *
     * @param stop the stop, which keys the delivery
     * @param order its order
     * @param attempt its attempt
     * @return the delivery, empty, never saved
     */
    public static Delivery firstFact(StopId stop, OrderId order, Attempt attempt) {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        return new Delivery(stop, order, attempt);
    }

    /**
     * Checks the delivery as Hibernate loaded it against the invariants that hold at rest (ADR-126
     * decision 8), never one that needs an outside fact.
     *
     * @throws StoredStateInvalid when the state breaks an invariant: a correction comes first or
     *     changes nothing, a report stays held beside an outcome, a held report is overtaken with no
     *     outcome, two held reports share an ID, a confirmed held report is not the original
     *     outcome, a reported outcome doesn't count from its report's time, or a driver's delivered
     *     lacks its proof
     */
    @PostLoad
    void checkStored() {
        DeliveryInvariants.brokenBy(outcomes(), heldReports()).ifPresent(broken -> {
            throw new StoredStateInvalid(Delivery.class, broken);
        });
    }

    /**
     * Checks the state a command left against every invariant at rest (ADR-126 decision 11). Every
     * rule a caller can break was checked before the command changed anything, so a broken
     * invariant here is a bug in the command, not a refusal, and the error rolls its transaction
     * back.
     */
    private void requireInvariants() {
        DeliveryInvariants.brokenBy(outcomes(), heldReports()).ifPresent(broken -> {
            throw new AssertionError("a delivery command left a state that breaks: " + broken);
        });
    }

    /**
     * The delivery's key.
     *
     * @return the stop
     */
    public StopId stop() {
        return StopId.of(id);
    }

    /**
     * The stop's order.
     *
     * @return the order
     */
    public OrderId order() {
        return order;
    }

    /**
     * Which attempt at the order the stop is.
     *
     * @return the attempt
     */
    public Attempt attempt() {
        return attempt;
    }

    /**
     * The driver's arrival (FR-29, BR-87).
     *
     * @return the arrival, or empty when none is recorded
     */
    public Optional<ArrivalRecord> arrival() {
        if (arrivalTime == null) {
            return Optional.empty();
        }
        return Optional.of(new ArrivalRecord(
                Objects.requireNonNull(arrivalDriver, "an arrival names its driver"),
                Objects.requireNonNull(arrivalRoute, "an arrival names its route"),
                arrivalTime));
    }

    /**
     * The outcomes on record: the original first, then each correction in order (FR-333). They hold
     * reasons, proofs and notes, so they are personal data and print redacted.
     *
     * @return an unmodifiable copy, empty while the stop has no outcome
     */
    public List<OutcomeRecord> outcomes() {
        return outcomes.stream().map(StoredOutcomeRecord::record).toList();
    }

    /**
     * Every report held for a dispatcher, open or decided, in the order held (BR-132). They hold
     * reasons, proofs and notes, so they are personal data and print redacted.
     *
     * @return an unmodifiable copy
     */
    public List<HeldReport> heldReports() {
        return heldReports.stream().map(StoredHeldReport::report).toList();
    }

    /**
     * Where the delivery stands, for {@code StopProgress} and {@code ExecutionFacts} (DL §9.3,
     * §9.6): the effective outcome, then an open held report, then an arrival.
     *
     * @return the derived state; a held report is not an outcome (BR-132), and {@link
     *     DeliveryStatus#HELD} hides an arrival recorded before it, which {@link #hasArrival()}
     *     still answers
     */
    public DeliveryStatus status() {
        if (!outcomes.isEmpty()) {
            return effective().outcome().kind() == OutcomeKind.DELIVERED
                    ? DeliveryStatus.DELIVERED
                    : DeliveryStatus.FAILED;
        }
        if (heldReports.stream().anyMatch(StoredHeldReport::isOpen)) {
            return DeliveryStatus.HELD;
        }
        return arrivalTime == null ? DeliveryStatus.PENDING : DeliveryStatus.ARRIVED;
    }

    /**
     * The outcome figures use: the latest correction, or the original (FR-333).
     *
     * @return the effective outcome, or empty when the stop has none
     */
    public Optional<Outcome> effectiveOutcome() {
        return outcomes.isEmpty() ? Optional.empty() : Optional.of(effective().outcome());
    }

    /**
     * When the effective outcome happened, the time Live Operations' view gives (ADR-107): the
     * report's corrected time, the time a dispatcher recorded it on a driver's behalf, or for a
     * correction the time it states, else the original's.
     *
     * @return the time, or empty when the stop has no outcome
     */
    public Optional<Instant> outcomeTime() {
        return outcomes.isEmpty() ? Optional.empty() : Optional.of(effective().factTime());
    }

    /**
     * Whether the driver's arrival is recorded, whatever the state shows. A rule that turns on the
     * stop being *arrived* or having an outcome, such as BR-92's order lock and FR-373's message
     * cut-off, asks this beside {@link #effectiveOutcome()}, because {@link #status()} shows
     * {@link DeliveryStatus#HELD} over an arrival while a report is held.
     *
     * @return {@code true} once an arrival is recorded
     */
    public boolean hasArrival() {
        return arrivalTime != null;
    }

    /**
     * When the driver arrived.
     *
     * @return the arrival's corrected time, or empty when none is recorded
     */
    public Optional<Instant> arrivedAt() {
        return Optional.ofNullable(arrivalTime).map(CorrectedTime::corrected);
    }

    /**
     * Records the driver's arrival (FR-29, BR-87). An arrival is never held (DL §9.3), and nothing
     * reacts to it alone (DL §7.4), so it raises no event. While a report is held it is recorded and
     * the state stays *held* (BR-132). An arrival from a driver the route was handed away from since
     * is refused, even for a time before the hand-over, because an arrival can't be held and it
     * would end the recipient's messages (FR-373, ADR-114).
     *
     * @param report who sent it, for which route, and when
     * @param entitlement Planning's answer for the report's route at its corrected time
     * @param where where the stop is now
     * @throws RuleViolation with {@link ReportAfterCancellation} when the report is timed at or after
     *     a cancellation (BR-90), {@link StopNotOnYourRoute} when the report's driver did not drive
     *     the route then or does not drive it now (BR-02, BR-110, ADR-114), {@link
     *     ArrivalAfterOutcome} when an outcome stands, or {@link ArrivalAlreadyRecorded}, in that
     *     order
     */
    public void arrive(DriverReport report, Entitlement entitlement, StopWhereabouts where) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(entitlement, "entitlement");
        Objects.requireNonNull(where, "where");
        refuseIfAfterCancellation(where, report.time());
        if (requireEntitled(entitlement, report).isHandedOverSince()) {
            throw new RuleViolation(new StopNotOnYourRoute(stop(), report.route()));
        }
        if (!outcomes.isEmpty()) {
            throw new RuleViolation(new ArrivalAfterOutcome(stop()));
        }
        if (arrivalTime != null) {
            throw new RuleViolation(new ArrivalAlreadyRecorded(stop()));
        }
        arriveAs(new ArrivalRecord(report.driver(), report.route(), report.time()));
        requireInvariants();
    }

    /**
     * Records a driver's *delivered* report, or holds it for a dispatcher (FR-29, BR-132). A report
     * from a driver the route was handed away from since is always held, whatever the contacts
     * (ADR-114).
     *
     * @param report who sent it, for which route, and when
     * @param proof the proof sent with it (FR-30)
     * @param leftAtDoor whether the driver left the goods at the door (FR-366)
     * @param door Customers &amp; Orders' answer on whether the order may be left at the door
     * @param entitlement Planning's answer for the report's route at its corrected time
     * @param check where the stop is now, and the account's contacts (BR-132)
     * @param ifHeld the ID the report takes if it is held
     * @param now when the server records it
     * @return what was done: recorded, with any held reports it overtook, or held
     * @throws RuleViolation with {@link ProofRequired} (FR-367), {@link ProofHasNoFile}, {@link
     *     NotALeaveAtDoorOrder} (FR-366), {@link LeftAtDoorNeedsPhoto} (FR-368), {@link
     *     ReportAfterCancellation} (BR-88, BR-90), {@link StopNotOnYourRoute} (BR-02, BR-110,
     *     ADR-114), {@link SameOutcomeAlreadyRecorded} or {@link OutcomeAlreadyRecorded} (BR-18,
     *     BR-88, BR-90, and against the same driver's open held report, ADR-107), in that order
     */
    public ReportAnswer deliver(
            DriverReport report,
            Optional<Proof> proof,
            boolean leftAtDoor,
            LeaveAtDoor door,
            Entitlement entitlement,
            HoldCheck check,
            HeldReportId ifHeld,
            Instant now) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(proof, "proof");
        Objects.requireNonNull(door, "door");
        requireReportParts(entitlement, check, ifHeld, now);
        if (proof.isEmpty()) {
            throw new RuleViolation(new ProofRequired(stop()));
        }
        if (proof.get().isErased()) {
            throw new RuleViolation(new ProofHasNoFile(stop()));
        }
        refuseIfNotADoorOrder(leftAtDoor, door);
        if (leftAtDoor && proof.get().kind() != ProofKind.PHOTO) {
            throw new RuleViolation(new LeftAtDoorNeedsPhoto(stop()));
        }
        return reportOutcome(
                report, Outcome.Delivered.withProof(proof.get(), leftAtDoor), entitlement, check, ifHeld, now);
    }

    /**
     * Records a driver's *failed* report, or holds it for a dispatcher (FR-29, FR-242, BR-132). A
     * report from a driver the route was handed away from since is always held, whatever the
     * contacts (ADR-114).
     *
     * @param report who sent it, for which route, and when
     * @param reason why it failed (BR-109)
     * @param entitlement Planning's answer for the report's route at its corrected time
     * @param check where the stop is now, and the account's contacts (BR-132)
     * @param ifHeld the ID the report takes if it is held
     * @param now when the server records it
     * @return what was done: recorded, with any held reports it overtook, or held
     * @throws RuleViolation with {@link ReportAfterCancellation} (BR-88, BR-90), {@link
     *     StopNotOnYourRoute} (BR-02, BR-110, ADR-114), {@link SameOutcomeAlreadyRecorded} or {@link
     *     OutcomeAlreadyRecorded} (BR-18, BR-88, BR-90, and against the same driver's open held
     *     report, ADR-107), in that order
     */
    public ReportAnswer fail(
            DriverReport report,
            FailureReason reason,
            Entitlement entitlement,
            HoldCheck check,
            HeldReportId ifHeld,
            Instant now) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(reason, "reason");
        requireReportParts(entitlement, check, ifHeld, now);
        return reportOutcome(report, new Outcome.Failed(reason), entitlement, check, ifHeld, now);
    }

    /**
     * Records an outcome a dispatcher made on a driver's behalf (FR-73, FR-257), needing no proof
     * (FR-367) and no entitlement. It overtakes every held report (BR-134) and starts no route run:
     * it takes no {@link DriverReport} (ADR-107).
     *
     * @param by the dispatcher
     * @param route the route whose driver it is recorded for
     * @param recorded what the dispatcher records, which can't carry a proof; a failure carries its
     *     reason (FR-73)
     * @param door Customers &amp; Orders' answer on whether the order may be left at the door
     * @param now when it is recorded, which is the time the outcome counts from
     * @return the held reports it overtook
     * @throws RuleViolation with {@link NotALeaveAtDoorOrder} (FR-366), then {@link
     *     SameOutcomeAlreadyRecorded} or {@link OutcomeAlreadyRecorded} (BR-18)
     */
    public ReportAnswer recordOnBehalf(
            AccountId by, RouteId route, DispatcherOutcome recorded, LeaveAtDoor door, Instant now) {
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(recorded, "recorded");
        Objects.requireNonNull(door, "door");
        Objects.requireNonNull(now, "now");
        Outcome outcome = recorded.toOutcome();
        refuseIfNotADoorOrder(outcome, door);
        refuseIfOutcomeStands(outcome);
        return recordOriginal(new OutcomeRecord(outcome, by, now, new Origin.OnBehalf(route)), route, true, now);
    }

    /**
     * Corrects the effective outcome, keeping the original beside it (FR-333). A correction may
     * change the kind, the reason or its text, or *left at the door* alone; the proof is not
     * compared, because a dispatcher's outcome never carries one (ADR-114). Correcting a failed stop
     * to delivered after a put-back is allowed while the put-back's stop has no outcome, and then
     * raises {@link PutBackSuperseded}, on which Live Operations' own listener asks Planning to take
     * that stop off (FR-404, ADR-114). A correction raises no other event: nothing reacts to one
     * (FR-374, ADR-064).
     *
     * @param corrected the outcome the stop now has, which can't carry a proof (FR-367)
     * @param reason why (FR-333)
     * @param statedTime when the corrected outcome happened, if the dispatcher states it, no later
     *     than {@code now}; else it counts from the original outcome's time (ADR-107)
     * @param by the dispatcher
     * @param later whether the order was put back after this stop, and what its new stop has
     * @param door Customers &amp; Orders' answer on whether the order may be left at the door
     * @param now when the correction is made
     * @throws RuleViolation with {@link NotALeaveAtDoorOrder} (FR-366), {@link
     *     CorrectionTimeInFuture}, {@link NoOutcomeToCorrect}, {@link CorrectionChangesNothing} or
     *     {@link LaterAttemptHasOutcome} (FR-404), in that order
     * @throws AssertionError when {@code later} names an attempt that is not after this stop's: a
     *     bug in the caller, not a refusal
     */
    public void correct(
            DispatcherOutcome corrected,
            CorrectionReason reason,
            Optional<Instant> statedTime,
            AccountId by,
            LaterAttempt later,
            LeaveAtDoor door,
            Instant now) {
        Objects.requireNonNull(corrected, "corrected");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(statedTime, "statedTime");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(later, "later");
        Objects.requireNonNull(door, "door");
        Objects.requireNonNull(now, "now");
        requireLater(later);
        Outcome to = corrected.toOutcome();
        refuseIfNotADoorOrder(to, door);
        if (statedTime.filter(stated -> stated.isAfter(now)).isPresent()) {
            throw new RuleViolation(new CorrectionTimeInFuture(stop()));
        }
        if (outcomes.isEmpty()) {
            throw new RuleViolation(new NoOutcomeToCorrect(stop()));
        }
        OutcomeKind from = effective().outcome().kind();
        if (to.statesExactly(effective().outcome())) {
            throw new RuleViolation(new CorrectionChangesNothing(stop(), from));
        }
        boolean failedToDelivered = from == OutcomeKind.FAILED && to.kind() == OutcomeKind.DELIVERED;
        if (failedToDelivered && later instanceof LaterAttempt.WithOutcome withOutcome) {
            throw new RuleViolation(new LaterAttemptHasOutcome(
                    stop(), withOutcome.stop(), withOutcome.attempt(), withOutcome.outcome()));
        }
        Instant factTime = statedTime.orElse(outcomes.get(0).record().factTime());
        outcomes.add(new StoredOutcomeRecord(new OutcomeRecord(to, by, factTime, new Origin.Correction(reason, now))));
        requireInvariants();
        if (failedToDelivered && later instanceof LaterAttempt.WithoutOutcome withoutOutcome) {
            raise(
                    PutBackSuperseded.class,
                    id,
                    eventId -> new PutBackSuperseded(
                            eventId,
                            now,
                            stop(),
                            order,
                            withoutOutcome.stop(),
                            withoutOutcome.attempt().number(),
                            withoutOutcome.date(),
                            by));
        }
    }

    /**
     * Confirms a held report: it becomes the stop's outcome, and every other held report is refused
     * as BR-90 says (BR-132, BR-18).
     *
     * @param report the held report
     * @param by the dispatcher
     * @param now when it is confirmed
     * @return the other held reports it refused
     * @throws RuleViolation with {@link ReportNotHeld} when the report is unknown, confirmed or
     *     refused, for example overtaken meanwhile (DL §11)
     */
    public ReportAnswer confirmHeld(HeldReportId report, AccountId by, Instant now) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(now, "now");
        HeldReport held = openHeld(report).report();
        List<HeldReportId> overtaken = new ArrayList<>();
        for (StoredHeldReport each : heldReports) {
            if (each.id().equals(report)) {
                each.moveTo(new HeldState.Confirmed(by, now));
            } else if (each.isOpen()) {
                each.moveTo(new HeldState.Overtaken(now));
                overtaken.add(each.id());
            }
        }
        // A report is held only while the stop has no outcome, so this one is the original.
        OutcomeRecord confirmed = new OutcomeRecord(
                held.outcome(), held.driver(), held.time().corrected(), new Origin.ConfirmedHeld(report));
        outcomes.add(new StoredOutcomeRecord(confirmed));
        requireInvariants();
        raiseRecorded(confirmed, held.route(), false, now);
        return new ReportAnswer.Recorded(overtaken);
    }

    /**
     * Refuses a held report, as BR-90 refuses a contradicting report (BR-135). The stop goes back
     * to *arrived* or *pending*, or stays *held* while another report is.
     *
     * @param report the held report
     * @param by the dispatcher
     * @param now when it is refused
     * @throws RuleViolation with {@link ReportNotHeld} when the report is unknown, confirmed or
     *     refused
     */
    public void refuseHeld(HeldReportId report, AccountId by, Instant now) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(now, "now");
        openHeld(report).moveTo(new HeldState.RefusedByDispatcher(by, now));
        requireInvariants();
    }

    /**
     * Erases the personal data whose retention has ended, and keeps every record (NFR-31, NFR-184,
     * ADR-107): the file and note of each proof whose report arrived 90 days or more ago, keeping
     * its kind, and the text of each *other* failure reason and each correction reason recorded 90
     * days or more ago, keeping the reason's kind. A sweep: a delivery with nothing due stays as
     * it is.
     *
     * @param now when the retention job runs
     */
    public void eraseExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        Instant proofCutoff = RetentionPeriods.proofCutoff(now);
        Instant reasonCutoff = RetentionPeriods.reasonCutoff(now);
        // Erasure keeps every receipt time, so the reports read here date the outcomes too.
        List<HeldReport> held = heldReports();
        for (StoredHeldReport stored : heldReports) {
            HeldReport each = stored.report();
            Outcome erased = erased(each.outcome(), each.time().receivedAt(), proofCutoff, reasonCutoff);
            if (!erased.equals(each.outcome())) {
                stored.restate(erased);
            }
        }
        for (int i = 0; i < outcomes.size(); i++) {
            OutcomeRecord each = outcomes.get(i).record();
            Origin origin = each.origin() instanceof Origin.Correction correction
                            && isDue(correction.correctedAt(), reasonCutoff)
                    ? new Origin.Correction(correction.reason().erased(), correction.correctedAt())
                    : each.origin();
            OutcomeRecord erased = new OutcomeRecord(
                    erased(each.outcome(), recordedAt(each, held), proofCutoff, reasonCutoff),
                    each.by(),
                    each.factTime(),
                    origin);
            // Only a record that changes is rewritten, so a sweep with nothing due writes nothing.
            if (!erased.equals(each)) {
                outcomes.set(i, new StoredOutcomeRecord(erased));
            }
        }
        requireInvariants();
    }

    /**
     * A form that names the stop and leaves its records out, because they are personal data.
     *
     * @return the delivery, redacted
     */
    @Override
    public String toString() {
        return "Delivery[stop=" + stop() + ", status=" + status() + ", redacted]";
    }

    private ReportAnswer reportOutcome(
            DriverReport report,
            Outcome outcome,
            Entitlement entitlement,
            HoldCheck check,
            HeldReportId ifHeld,
            Instant now) {
        CorrectedTime time = report.time();
        refuseIfAfterCancellation(check.where(), time);
        Entitlement.OnRoute entitled = requireEntitled(entitlement, report);
        RevisionNo revision = entitled.revision();
        refuseIfOutcomeStands(outcome);
        refuseIfOwnReportHeld(report, outcome);
        Optional<CancellationKind> overridden = check.where().cancelledAfter(time);
        boolean anyHeld = heldReports.stream().anyMatch(StoredHeldReport::isOpen);
        boolean hold = entitled.isHandedOverSince()
                || (anyHeld ? !(check.where() instanceof StopWhereabouts.OnServingRoute) : check.holds(time));
        if (hold) {
            heldReports.add(new StoredHeldReport(new HeldReport(
                    ifHeld,
                    outcome,
                    report.driver(),
                    report.route(),
                    revision,
                    time,
                    overridden,
                    new HeldState.Open())));
            requireInvariants();
            return new ReportAnswer.Held(ifHeld);
        }
        OutcomeRecord recorded = new OutcomeRecord(
                outcome,
                report.driver(),
                time.corrected(),
                new Origin.Reported(report.route(), revision, time, overridden));
        return recordOriginal(recorded, report.route(), false, now);
    }

    /** Records the original outcome, overtaking every open held report (BR-134, BR-18). */
    private ReportAnswer recordOriginal(OutcomeRecord original, RouteId route, boolean onBehalf, Instant now) {
        List<HeldReportId> overtaken = new ArrayList<>();
        for (StoredHeldReport each : heldReports) {
            if (each.isOpen()) {
                each.moveTo(new HeldState.Overtaken(now));
                overtaken.add(each.id());
            }
        }
        // Every caller refused the call while an outcome stands, so this one is the original.
        outcomes.add(new StoredOutcomeRecord(original));
        requireInvariants();
        raiseRecorded(original, route, onBehalf, now);
        return new ReportAnswer.Recorded(overtaken);
    }

    private void raiseRecorded(OutcomeRecord recorded, RouteId route, boolean onBehalf, Instant now) {
        raise(
                OutcomeRecorded.class,
                id,
                eventId -> new OutcomeRecorded(
                        eventId,
                        now,
                        stop(),
                        order,
                        attempt.number(),
                        route,
                        recorded.outcome().kind(),
                        recorded.outcome() instanceof Outcome.Delivered delivered && delivered.leftAtDoor(),
                        recorded.factTime(),
                        onBehalf,
                        recorded.by()));
    }

    private static void requireReportParts(Entitlement entitlement, HoldCheck check, HeldReportId ifHeld, Instant now) {
        Objects.requireNonNull(entitlement, "entitlement");
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(ifHeld, "ifHeld");
        Objects.requireNonNull(now, "now");
    }

    private void refuseIfAfterCancellation(StopWhereabouts where, CorrectedTime time) {
        if (where instanceof StopWhereabouts.Cancelled cancelled && cancelled.isNotAfter(time)) {
            throw new RuleViolation(new ReportAfterCancellation(stop(), cancelled.kind()));
        }
    }

    /**
     * A report is entitled only when the stop was on its route and its sender drove that route in
     * the revision in force at its corrected time, so a driver the route was handed away from can't
     * report on it past the hand-over (BR-02, BR-110, ADR-114).
     */
    private Entitlement.OnRoute requireEntitled(Entitlement entitlement, DriverReport report) {
        if (entitlement instanceof Entitlement.OnRoute onRoute
                && onRoute.driver().equals(report.driver())) {
            return onRoute;
        }
        throw new RuleViolation(new StopNotOnYourRoute(stop(), report.route()));
    }

    /**
     * A driver and route that already have a report held on the stop can't add another, so a
     * phone can't flood the dispatcher with held reports (BR-18, ADR-107): a repeat is refused
     * silently, and a different one as a contradiction.
     */
    private void refuseIfOwnReportHeld(DriverReport report, Outcome incoming) {
        Optional<HeldReport> own = heldReports().stream()
                .filter(each -> each.isOpen()
                        && each.driver().equals(report.driver())
                        && each.route().equals(report.route()))
                .findFirst();
        if (own.isEmpty()) {
            return;
        }
        Outcome held = own.get().outcome();
        if (incoming.isSameAs(held)) {
            throw new RuleViolation(new SameOutcomeAlreadyRecorded(stop(), held.kind()));
        }
        throw new RuleViolation(new OutcomeAlreadyRecorded(stop(), held.kind()));
    }

    private void refuseIfOutcomeStands(Outcome incoming) {
        if (outcomes.isEmpty()) {
            return;
        }
        Outcome current = effective().outcome();
        if (incoming.isSameAs(current)) {
            throw new RuleViolation(new SameOutcomeAlreadyRecorded(stop(), current.kind()));
        }
        throw new RuleViolation(new OutcomeAlreadyRecorded(stop(), current.kind()));
    }

    private void refuseIfNotADoorOrder(Outcome outcome, LeaveAtDoor door) {
        refuseIfNotADoorOrder(outcome instanceof Outcome.Delivered delivered && delivered.leftAtDoor(), door);
    }

    private void refuseIfNotADoorOrder(boolean leftAtDoor, LeaveAtDoor door) {
        if (leftAtDoor && door != LeaveAtDoor.ALLOWED) {
            throw new RuleViolation(new NotALeaveAtDoorOrder(stop()));
        }
    }

    private void requireLater(LaterAttempt later) {
        Optional<Attempt> laterAttempt = switch (later) {
            case LaterAttempt.None none -> Optional.empty();
            case LaterAttempt.WithoutOutcome withoutOutcome -> Optional.of(withoutOutcome.attempt());
            case LaterAttempt.WithOutcome withOutcome -> Optional.of(withOutcome.attempt());
        };
        if (laterAttempt.filter(each -> each.compareTo(attempt) <= 0).isPresent()) {
            throw new AssertionError("a later attempt comes after this stop's attempt");
        }
    }

    private StoredHeldReport openHeld(HeldReportId report) {
        return heldReports.stream()
                .filter(each -> each.id().equals(report) && each.isOpen())
                .findFirst()
                .orElseThrow(() -> new RuleViolation(new ReportNotHeld(stop(), report)));
    }

    private OutcomeRecord effective() {
        return outcomes.get(outcomes.size() - 1).record();
    }

    private void arriveAs(ArrivalRecord record) {
        arrivalDriver = record.driver();
        arrivalRoute = record.route();
        arrivalTime = record.time();
    }

    /** When an outcome's personal data reached the server, which its retention counts from. */
    private static Instant recordedAt(OutcomeRecord record, List<HeldReport> held) {
        return switch (record.origin()) {
            case Origin.Reported reported -> reported.time().receivedAt();
            case Origin.ConfirmedHeld confirmed ->
                held.stream()
                        .filter(each -> each.id().equals(confirmed.report()))
                        .map(each -> each.time().receivedAt())
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("a confirmed outcome names its held report"));
            case Origin.OnBehalf onBehalf -> record.factTime();
            case Origin.Correction correction -> correction.correctedAt();
        };
    }

    private static boolean isDue(Instant recordedAt, Instant cutoff) {
        return !recordedAt.isAfter(cutoff);
    }

    private static Outcome erased(Outcome outcome, Instant recordedAt, Instant proofCutoff, Instant reasonCutoff) {
        return switch (outcome) {
            case Outcome.Delivered delivered ->
                isDue(recordedAt, proofCutoff)
                        ? new Outcome.Delivered(delivered.proof().map(Proof::erased), delivered.leftAtDoor())
                        : delivered;
            case Outcome.Failed failed ->
                isDue(recordedAt, reasonCutoff)
                        ? new Outcome.Failed(failed.reason().erased())
                        : failed;
        };
    }
}
