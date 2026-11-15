package com.dispatchflow.liveops.domain;

import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;

/**
 * Every refusal of the Live Operations context (DL §3.8, §4.5). The fields become the response's
 * parameters, so they hold IDs, numbers and this context's own enums, never a reason's text, a
 * proof or a note. The rules no document settled, and the names they needed, are ADR-107's.
 */
public sealed interface LiveOpsError extends DomainError {

    /**
     * The stop was not on the reporting driver's route in the revision in force at the report's
     * corrected time: it was on no such route, or that route had another driver then, as after a
     * hand-over (BR-02, BR-110, BR-114, ADR-114).
     *
     * @param stop the stop
     * @param route the route the report came from
     */
    record StopNotOnYourRoute(StopId stop, RouteId route) implements LiveOpsError {}

    /**
     * A driver started or finished a route that another driver drove in the revision in force at
     * the report's corrected time, as after a hand-over (BR-02, FR-68, ADR-114).
     *
     * @param route the route
     */
    record NotYourRoute(RouteId route) implements LiveOpsError {}

    /**
     * A report's device time or device clock lies outside the years 1 to 9999, so no corrected time
     * can be computed from it (BR-114, ADR-114).
     */
    record DeviceTimeOutOfRange() implements LiveOpsError {}

    /**
     * The report's corrected time is at or after its order was cancelled or rescheduled, or its
     * plan cancelled; it is shown to the dispatcher (BR-88, BR-90, ADR-107).
     *
     * @param stop the stop
     * @param kind what happened to the order or plan
     */
    record ReportAfterCancellation(StopId stop, CancellationKind kind) implements LiveOpsError {}

    /**
     * The stop already has an outcome, or the same driver's open held report states it, and the
     * report contradicts it; it is shown to the dispatcher (BR-18, BR-88, BR-90, BR-136).
     *
     * @param stop the stop
     * @param recorded the outcome the stop has, or the held report's kind
     */
    record OutcomeAlreadyRecorded(StopId stop, OutcomeKind recorded) implements LiveOpsError {}

    /**
     * The stop already has this same outcome, or the same driver's open held report states it; the
     * repeat is refused without telling the dispatcher (BR-88, BR-136, ADR-107).
     *
     * @param stop the stop
     * @param recorded the outcome the stop has, or the held report's kind
     */
    record SameOutcomeAlreadyRecorded(StopId stop, OutcomeKind recorded) implements LiveOpsError {}

    /**
     * The stop already has an arrival (BR-87).
     *
     * @param stop the stop
     */
    record ArrivalAlreadyRecorded(StopId stop) implements LiveOpsError {}

    /**
     * The stop already has an outcome, so an arrival comes too late (BR-87, BR-18).
     *
     * @param stop the stop
     */
    record ArrivalAfterOutcome(StopId stop) implements LiveOpsError {}

    /**
     * A driver's *delivered* came without a proof (FR-367).
     *
     * @param stop the stop
     */
    record ProofRequired(StopId stop) implements LiveOpsError {}

    /**
     * A driver's proof names no uploaded file (FR-30, FR-367, ADR-107).
     *
     * @param stop the stop
     */
    record ProofHasNoFile(StopId stop) implements LiveOpsError {}

    /**
     * Only a leave-at-door order is recorded as left at the door (FR-366).
     *
     * @param stop the stop
     */
    record NotALeaveAtDoorOrder(StopId stop) implements LiveOpsError {}

    /**
     * A driver's stop left at the door needs a photo as its proof, because nobody is there to sign
     * (FR-368).
     *
     * @param stop the stop
     */
    record LeftAtDoorNeedsPhoto(StopId stop) implements LiveOpsError {}

    /** The failure reason *other* came without its text (BR-109, FR-242). */
    record FailureReasonRequired() implements LiveOpsError {}

    /** A correction came without its reason (FR-333). */
    record CorrectionReasonRequired() implements LiveOpsError {}

    /**
     * The stop has no outcome to correct (FR-333).
     *
     * @param stop the stop
     */
    record NoOutcomeToCorrect(StopId stop) implements LiveOpsError {}

    /**
     * The correction states exactly the outcome the stop already has: the same kind, the same *left
     * at the door*, and for a failure the same reason with its text (FR-333, ADR-107, ADR-114).
     *
     * @param stop the stop
     * @param outcome the outcome it has
     */
    record CorrectionChangesNothing(StopId stop, OutcomeKind outcome) implements LiveOpsError {}

    /**
     * The correction states a time after it was made (FR-333, ADR-107).
     *
     * @param stop the stop
     */
    record CorrectionTimeInFuture(StopId stop) implements LiveOpsError {}

    /**
     * A failed stop can't be corrected to delivered, because the order's put-back stop already has
     * an outcome; the error names that stop and its outcome (FR-404, ADR-107).
     *
     * @param stop the stop being corrected
     * @param laterStop the put-back's stop
     * @param laterAttempt the put-back's attempt
     * @param laterOutcome the outcome it has
     */
    record LaterAttemptHasOutcome(StopId stop, StopId laterStop, Attempt laterAttempt, OutcomeKind laterOutcome)
            implements LiveOpsError {}

    /**
     * The report is not held: it is unknown, or already confirmed or refused (BR-132, DL §11).
     *
     * @param stop the stop
     * @param report the report
     */
    record ReportNotHeld(StopId stop, HeldReportId report) implements LiveOpsError {}

    /**
     * The route has already started (FR-68, FR-244).
     *
     * @param route the route
     */
    record RunAlreadyStarted(RouteId route) implements LiveOpsError {}

    /**
     * The route has already finished (FR-68, DL §4.5).
     *
     * @param route the route
     */
    record RouteAlreadyFinished(RouteId route) implements LiveOpsError {}

    /**
     * The route keeps no position from this sender at this time (FR-328, NFR-30).
     *
     * @param route the route
     * @param reason why not
     */
    record TrackingClosed(RouteId route, TrackingRefusal reason) implements LiveOpsError {}

    /**
     * A revision number below 1 (FR-97).
     *
     * @param number the number given
     */
    record InvalidRevision(int number) implements LiveOpsError {}

    /**
     * The driver confirmed a revision the plan doesn't have yet (FR-97, ADR-107).
     *
     * @param route the route
     * @param seen the revision the phone sent
     * @param current the plan's current revision
     */
    record UnknownRevision(RouteId route, RevisionNo seen, RevisionNo current) implements LiveOpsError {}

    /**
     * The alert was already acknowledged, which records who and when once (FR-260).
     *
     * @param alert the alert
     */
    record AlertAlreadyAcknowledged(AlertId alert) implements LiveOpsError {}
}
