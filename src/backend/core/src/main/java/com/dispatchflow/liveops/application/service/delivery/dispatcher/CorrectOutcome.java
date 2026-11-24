package com.dispatchflow.liveops.application.service.delivery.dispatcher;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.domain.outcome.CorrectionReason;
import com.dispatchflow.liveops.domain.outcome.DispatcherOutcome;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A dispatcher corrects a stop's recorded outcome, with a reason; the original stays in the
 * history, and figures use the correction (FR-333, FR-374). A correction sends no message.
 *
 * <p>{@link CorrectOutcomeChange#apply} holds the transaction, and reads first: Planning's {@link
 * RouteMembership#laterStopOf} for a put-back after the stop's attempt, whose stop's own delivery
 * tells whether it has an outcome, which together make the domain's {@code LaterAttempt} (FR-258,
 * FR-404, ADR-107); and Customers &amp; Orders' {@link StopOrders} for the door flag. Then it calls
 * {@code Delivery.correct} with the audit record. Correcting a failed stop to delivered after a
 * put-back raises {@code PutBackSuperseded}, which takes the put-back's stop off its plan and
 * returns the order to its earlier attempt (FR-404, ADR-114, ADR-121). Like every changing call it
 * writes its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110). After the
 * commit, it tells the open screens through {@link LiveUpdates}.
 *
 * <p>To Be Implemented. Rows: FR-333, FR-374, FR-404.
 */
public final class CorrectOutcome {

    private final LiveUpdates liveUpdates;
    private final CorrectOutcomeChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the open screens
     * @param change the use case's transaction
     */
    public CorrectOutcome(LiveUpdates liveUpdates, CorrectOutcomeChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Corrects the outcome.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param stop the stop whose outcome is corrected
     * @param command the corrected outcome, the reason and the stated time
     * @param expected the delivery's version the dispatcher saw (BR-76)
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     NotALeaveAtDoorOrder}, {@code CorrectionTimeInFuture}, {@code NoOutcomeToCorrect}, {@code
     *     CorrectionChangesNothing} or {@code LaterAttemptHasOutcome} (FR-404), in that order
     * @throws NotFound when nothing is recorded for the stop
     * @throws StaleVersion when the delivery changed since {@code expected}
     */
    public void correct(Actor actor, StopId stop, Command command, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * A correction as the dispatcher states it. The outcome and the reason can name a person, so it
     * prints redacted.
     *
     * <p>To Be Implemented.
     *
     * @param corrected the outcome the stop now has, which carries no proof (FR-367)
     * @param reason why (FR-333)
     * @param statedTime when the corrected outcome happened, if the dispatcher states it, no later
     *     than now; else it counts from the original's time (ADR-107)
     */
    public record Command(DispatcherOutcome corrected, CorrectionReason reason, Optional<Instant> statedTime)
            implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public Command {
            Objects.requireNonNull(corrected, "corrected");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(statedTime, "statedTime");
        }

        /**
         * A form that hides the outcome and the reason.
         *
         * @return the redacted form
         */
        @Override
        public String toString() {
            return "Command[redacted]";
        }
    }
}
