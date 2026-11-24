package com.dispatchflow.reporting.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.events.PlanPublished;
import com.dispatchflow.reporting.application.port.out.BaselineAnswer;
import com.dispatchflow.reporting.application.port.out.BaselineRepository;
import com.dispatchflow.reporting.application.port.out.BaselineRequest;
import com.dispatchflow.reporting.application.port.out.ReportingViews;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link ComputeBaseline}, which hold none of its outside calls (ADR-129
 * decision 3): one builds the {@code BaselineRequest} before the solve, and one stores the
 * planner's answer with its audit record.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class ComputeBaselineChange {

    private final ReportingViews views;
    private final BaselineRepository baselines;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the listener's transactions.
     *
     * @param views Reporting's read port over the views other modules publish
     * @param baselines where baselines are kept
     * @param clock the time of computing
     * @param auditLog the audit trail
     */
    public ComputeBaselineChange(ReportingViews views, BaselineRepository baselines, Clock clock, AuditLog auditLog) {
        this.views = views;
        this.baselines = baselines;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Reads the plan's {@code BaselineInputs} through {@link ReportingViews#baselineInputsOf}, and
     * builds the request from them with no vehicle withdrawn and with {@code BaselineSolve}'s seed
     * and budget.
     *
     * <p>To Be Implemented.
     *
     * @param event the publication
     * @return what the planner is asked
     * @throws NotFound when Planning's view has no published plan with the event's ID, which only a
     *     bug causes
     */
    @Transactional(readOnly = true)
    public BaselineRequest requestFor(PlanPublished event) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Stores the planner's answer: {@code Baseline.compute} for a solve, or the cause of a {@link
     * BaselineAnswer.Refused} through {@link BaselineRepository#recordRefusal}, with the audit
     * record. A plan that already holds either changes nothing.
     *
     * <p>To Be Implemented.
     *
     * @param event the publication
     * @param answer what the planner answered
     * @param call the listener's server step, which the audit record is written with
     * @throws RuleViolation with Reporting's {@code NegativeDistance}, {@code NegativeDriverHours}
     *     or {@code OrderCountedTwice} when the optimizer's answer translates into no valid figures
     * @throws StaleVersion when a concurrent delivery stored the plan's baseline or refusal first;
     *     the listener adapter retries the event, and the retry finds the plan settled
     */
    public void apply(PlanPublished event, BaselineAnswer answer, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
