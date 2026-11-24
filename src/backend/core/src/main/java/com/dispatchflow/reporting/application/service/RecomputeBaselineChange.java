package com.dispatchflow.reporting.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.reporting.application.port.out.BaselineAnswer;
import com.dispatchflow.reporting.application.port.out.BaselineRepository;
import com.dispatchflow.reporting.application.port.out.BaselineRequest;
import com.dispatchflow.reporting.application.port.out.ReportingViews;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.ProcessedEvents;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link RecomputeBaseline}, which hold none of its outside calls (ADR-129
 * decision 3): one builds the {@code BaselineRequest} from the stored baseline before the solve,
 * and one stores the recomputed baseline with its audit record and the {@link ProcessedEvents}
 * pair. The second checks that the baseline is still at the version the first read, because the
 * vehicles the solve leaves out came from that version.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class RecomputeBaselineChange {

    private final ReportingViews views;
    private final BaselineRepository baselines;
    private final ProcessedEvents processedEvents;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the listener's transactions.
     *
     * @param views Reporting's read port over the views other modules publish
     * @param baselines where baselines are kept
     * @param processedEvents the listeners' idempotency record
     * @param clock the time of computing
     * @param auditLog the audit trail
     */
    public RecomputeBaselineChange(
            ReportingViews views,
            BaselineRepository baselines,
            ProcessedEvents processedEvents,
            Clock clock,
            AuditLog auditLog) {
        this.views = views;
        this.baselines = baselines;
        this.processedEvents = processedEvents;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Builds the request a revision calls for: {@code Baseline.withdrawnFleetAfter} with the
     * event's withdrawals, the plan's {@code BaselineInputs} through {@link
     * ReportingViews#baselineInputsOf}, and {@code BaselineSolve}'s seed and budget.
     *
     * <p>To Be Implemented.
     *
     * @param event the revision
     * @return the solve to ask for; empty when the revision neither withdrew a vehicle nor added
     *     orders, or when the publication's baseline was refused, which {@link
     *     BaselineRepository#refusalOf} answers
     * @throws NotFound when Planning's view has no published plan with the event's ID, or the plan
     *     has neither a baseline nor a recorded refusal yet, so the delivery fails and is
     *     resubmitted until the publication's listener has run
     */
    @Transactional(readOnly = true)
    public Optional<Solve> requestFor(PlanRevised event) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Stores the planner's answer: {@code Baseline.recompute} for a solve, and no new baseline for
     * a {@link BaselineAnswer.Refused}. It records the {@code (listener, event ID)} pair in {@link
     * ProcessedEvents} and writes the audit record, and a repeated delivery changes nothing.
     *
     * <p>To Be Implemented.
     *
     * @param event the revision
     * @param expected the version of the baseline the request was built from
     * @param answer what the planner answered
     * @param call the listener's server step, which the audit record is written with
     * @throws RuleViolation with Reporting's {@code NegativeDistance}, {@code NegativeDriverHours}
     *     or {@code OrderCountedTwice} when the optimizer's answer translates into no valid figures
     * @throws StaleVersion when the baseline is no longer at {@code expected}, or another recompute
     *     of the plan was saved before the save; the listener's adapter retries at once, up to
     *     three times (DL §11)
     */
    public void apply(PlanRevised event, Version expected, BaselineAnswer answer, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * The solve a revision calls for, and the baseline it was built from. Its request holds
     * personal data, so it prints redacted.
     *
     * @param request what the planner is asked
     * @param baseline the version of the stored baseline the request's withdrawn fleet came from
     */
    public record Solve(BaselineRequest request, Version baseline) implements PersonalData {

        /** Checks every part is present. */
        public Solve {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(baseline, "baseline");
        }

        /**
         * A form that hides the request.
         *
         * @return {@code RecomputeBaselineChange.Solve[redacted]}
         */
        @Override
        public String toString() {
            return "RecomputeBaselineChange.Solve[redacted]";
        }
    }
}
