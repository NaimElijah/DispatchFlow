package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.domain.delivery.CorrectionReason;
import com.dispatchflow.liveops.domain.delivery.DispatcherOutcome;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A dispatcher corrects a stop's recorded outcome, with a reason; the original stays in the
 * history, and figures use the correction (FR-333, FR-374). A correction sends no message.
 *
 * <p>Outside calls first: Planning's {@link RouteMembership#laterStopOf} for a put-back after the
 * stop's attempt, whose stop's own delivery tells whether it has an outcome, which together make the
 * domain's {@code LaterAttempt} (FR-258, FR-404, ADR-107); and Customers &amp; Orders' {@link
 * StopOrders} for the door flag. Then one transaction calls {@code Delivery.correct} with the audit
 * record. Correcting a failed stop to delivered after a put-back raises {@code PutBackSuperseded},
 * which takes the put-back's stop off its plan (FR-404, ADR-114).
 *
 * <p>Built in M8. Rows: FR-333, FR-374, FR-404.
 */
public final class CorrectOutcome {

    private final DeliveryRepository deliveries;
    private final RouteMembership membership;
    private final StopOrders stopOrders;
    private final Clock clock;
    private final AuditLog auditLog;
    private final LiveUpdates liveUpdates;

    /**
     * Creates the use case.
     *
     * @param deliveries where deliveries are kept
     * @param membership Planning's answers on a put-back after a stop
     * @param stopOrders Customers &amp; Orders' door flag of an order
     * @param clock the server's time
     * @param auditLog the audit trail
     * @param liveUpdates the open screens
     */
    public CorrectOutcome(
            DeliveryRepository deliveries,
            RouteMembership membership,
            StopOrders stopOrders,
            Clock clock,
            AuditLog auditLog,
            LiveUpdates liveUpdates) {
        this.deliveries = deliveries;
        this.membership = membership;
        this.stopOrders = stopOrders;
        this.clock = clock;
        this.auditLog = auditLog;
        this.liveUpdates = liveUpdates;
    }

    /**
     * Corrects the outcome.
     *
     * <p>Built in M8.
     *
     * @param actor the dispatcher
     * @param command the stop, the corrected outcome, the reason, the stated time and the version
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     NotALeaveAtDoorOrder}, {@code CorrectionTimeInFuture}, {@code NoOutcomeToCorrect}, {@code
     *     CorrectionChangesNothing} or {@code LaterAttemptHasOutcome} (FR-404), in that order
     * @throws NotFound when nothing is recorded for the stop
     * @throws StaleVersion when the delivery changed since {@code command.expected()}
     */
    public void correct(Actor actor, Command command) {
        throw new UnsupportedOperationException("Built in M8");
    }

    /**
     * A correction as the dispatcher states it. The outcome and the reason can name a person, so it
     * prints redacted.
     *
     * <p>Built in M8.
     *
     * @param stop the stop
     * @param corrected the outcome the stop now has, which carries no proof (FR-367)
     * @param reason why (FR-333)
     * @param statedTime when the corrected outcome happened, if the dispatcher states it, no later
     *     than now; else it counts from the original's time (ADR-107)
     * @param expected the delivery's version the dispatcher saw (BR-76)
     */
    public record Command(
            StopId stop,
            DispatcherOutcome corrected,
            CorrectionReason reason,
            Optional<Instant> statedTime,
            Version expected)
            implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public Command {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(corrected, "corrected");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(statedTime, "statedTime");
            Objects.requireNonNull(expected, "expected");
        }

        /**
         * A form that names the stop and hides the outcome and the reason.
         *
         * @return the stop and the version, redacted
         */
        @Override
        public String toString() {
            return "Command[stop=" + stop + ", expected=" + expected + ", redacted]";
        }
    }
}
