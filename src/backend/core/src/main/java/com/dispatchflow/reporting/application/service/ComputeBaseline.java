package com.dispatchflow.reporting.application.service;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.events.PlanPublished;
import com.dispatchflow.reporting.application.port.out.BaselineAnswer;
import com.dispatchflow.reporting.application.port.out.BaselineOptimizerUnavailable;
import com.dispatchflow.reporting.application.port.out.BaselinePlanner;
import com.dispatchflow.reporting.application.port.out.BaselineRepository;
import com.dispatchflow.reporting.application.port.out.ReportingViews;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Computes a published plan's baseline when Reporting hears {@code PlanPublished} (FR-39, FR-342,
 * DL §7.2). Before the transaction that stores it, it reads the plan's {@code BaselineInputs}
 * through {@link ReportingViews#baselineInputsOf}, from the views Planning and Customers &amp;
 * Orders publish (ADR-041, ADR-109), builds the {@code BaselineRequest} from them with no vehicle
 * withdrawn and with {@code BaselineSolve}'s seed and budget, and asks the {@link BaselinePlanner}
 * for the nearest-neighbour solve (DL §6.3); then {@code Baseline.compute} stores it. It reads no
 * other module's Java API. Delivery is at least once (ADR-064): the plan keys the baseline and the
 * refusal, so a repeated delivery finds one stored and changes nothing.
 *
 * <p>When the planner answers {@link BaselineAnswer.Refused}, the use case stores no baseline: it
 * records the refusal's cause through {@link BaselineRepository#recordRefusal} and completes, so
 * the delivery isn't resubmitted 5 times and then alerted (ADR-064) for a refusal that asking again
 * may repeat, and a later recompute finds the plan settled. What the plan's summary shows then is
 * open for M5.
 *
 * <p>The {@link BaselinePlanner} is an outside call, so this class opens no transaction: {@link
 * ComputeBaselineChange} reads the inputs and builds the request in one, before the solve, and
 * stores the answer in another (ADR-129).
 *
 * <p>Built in M5.
 */
public final class ComputeBaseline {

    private final BaselinePlanner baselinePlanner;
    private final ComputeBaselineChange change;

    /**
     * Creates the listener's use case.
     *
     * @param baselinePlanner the optimizer's baseline solve
     * @param change the transactions that build the request and store the answer
     */
    public ComputeBaseline(BaselinePlanner baselinePlanner, ComputeBaselineChange change) {
        this.baselinePlanner = baselinePlanner;
        this.change = change;
    }

    /**
     * Computes the baseline of the plan just published.
     *
     * <p>Built in M5.
     *
     * @param event the publication
     * @param call the listener's server step, which the audit record is written with
     * @throws NotFound when Planning's view has no published plan with the event's ID, which only a
     *     bug causes
     * @throws BaselineOptimizerUnavailable when the optimizer can't be reached, so the delivery
     *     fails and is resubmitted (ADR-064)
     * @throws RuleViolation with Reporting's {@code NegativeDistance}, {@code NegativeDriverHours}
     *     or {@code OrderCountedTwice} when the optimizer's answer translates into no valid figures
     * @throws StaleVersion when a concurrent delivery stored the plan's baseline or refusal first;
     *     the listener adapter retries the event, and the retry finds the plan settled
     */
    public void on(PlanPublished event, CallContext call) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
