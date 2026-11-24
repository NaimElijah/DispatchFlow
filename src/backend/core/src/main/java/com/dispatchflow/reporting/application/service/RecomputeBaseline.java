package com.dispatchflow.reporting.application.service;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.reporting.application.port.out.BaselineAnswer;
import com.dispatchflow.reporting.application.port.out.BaselineOptimizerUnavailable;
import com.dispatchflow.reporting.application.port.out.BaselinePlanner;
import com.dispatchflow.reporting.application.port.out.BaselineRepository;
import com.dispatchflow.reporting.application.port.out.ReportingViews;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.ProcessedEvents;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Recomputes a plan's baseline when Reporting hears a {@code PlanRevised} that withdrew a vehicle
 * mid-route or added orders, so both sides cover the same orders and fleet (FR-39, FR-267 to
 * FR-271, ADR-109, ADR-112). A revision that did neither changes nothing. Each withdrawal's cause
 * is mapped one to one into Reporting's {@code WithdrawalCause}, and the added orders are the
 * event's. Before the transaction that stores it, it asks {@code Baseline.withdrawnFleetAfter}
 * which vehicles the solve leaves out and which it gives their whole shift (FR-271), reads the
 * plan's {@code BaselineInputs} through {@link ReportingViews#baselineInputsOf}, from the views
 * Planning and Customers &amp; Orders publish (ADR-041, ADR-109), builds the {@code
 * BaselineRequest} from both with {@code BaselineSolve}'s seed and budget, the publication's own
 * (NFR-13), and asks the {@link BaselinePlanner} for the solve (DL §6.3); then {@code
 * Baseline.recompute} stores it. It reads no other module's Java API.
 *
 * <p>Delivery is at least once (ADR-064), and a recompute is not idempotent by nature, because it
 * stores a new solve: the use case records the {@code (listener, event ID)} pair in {@link
 * ProcessedEvents} in the transaction that stores it, and a repeated delivery changes nothing.
 *
 * <p>For a plan whose publication baseline was refused, which {@link
 * BaselineRepository#refusalOf} answers, there is nothing to recompute, and the use case completes.
 * With neither a baseline nor a refusal recorded, the publication's listener hasn't run yet, so the
 * use case throws {@link NotFound} and the delivery is resubmitted until it has.
 *
 * <p>When the planner answers {@link BaselineAnswer.Refused}, the use case stores no new baseline
 * and completes, so the delivery isn't resubmitted 5 times and then alerted (ADR-064) for a refusal
 * that asking again may repeat. What the plan's summary shows then is decided when the baseline
 * comparison is built.
 *
 * <p>The {@link BaselinePlanner} is an outside call, so this class opens no transaction: {@link
 * RecomputeBaselineChange} reads the baseline and the inputs and builds the request in one, before
 * the solve, and stores the answer in another, if the baseline is still the one the request was
 * built from (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class RecomputeBaseline {

    private final BaselinePlanner baselinePlanner;
    private final RecomputeBaselineChange change;

    /**
     * Creates the listener's use case.
     *
     * @param baselinePlanner the optimizer's baseline solve
     * @param change the transactions that build the request and store the answer
     */
    public RecomputeBaseline(BaselinePlanner baselinePlanner, RecomputeBaselineChange change) {
        this.baselinePlanner = baselinePlanner;
        this.change = change;
    }

    /**
     * Recomputes the baseline after a revision, if the revision withdrew a vehicle or added orders.
     *
     * <p>To Be Implemented.
     *
     * @param event the revision
     * @param call the listener's server step, which the audit record is written with
     * @throws NotFound when Planning's view has no published plan with the event's ID, or the plan
     *     has neither a baseline nor a recorded refusal yet, so the delivery fails and is
     *     resubmitted until the publication's listener has run
     * @throws BaselineOptimizerUnavailable when the optimizer can't be reached, so the delivery
     *     fails and is resubmitted (ADR-064)
     * @throws RuleViolation with Reporting's {@code NegativeDistance}, {@code NegativeDriverHours}
     *     or {@code OrderCountedTwice} when the optimizer's answer translates into no valid figures
     * @throws StaleVersion when another recompute of the plan was saved since the baseline was
     *     loaded; the listener's adapter retries at once, up to three times (DL §11)
     */
    public void on(PlanRevised event, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
