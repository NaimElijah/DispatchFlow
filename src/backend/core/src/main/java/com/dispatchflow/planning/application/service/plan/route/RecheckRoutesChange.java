package com.dispatchflow.planning.application.service.plan.route;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.domain.plan.PlanLayout;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reads and the transactions of {@link RecheckRoutes}. {@link #due} lists the plans with an
 * unchecked route and {@link #layoutOf} reads one plan as it is, each in a read-only transaction.
 * {@link #apply} then loads the plan again, checks the version that read returned, records the check
 * with {@code Plan.recordRouteCheck}, saves the plan and writes the audit record, in a transaction of
 * its own for each plan (DL §6.3, §12, TS §6.2, ADR-063, ADR-129).
 *
 * <p>Built in M9.
 */
@Transactional
public class RecheckRoutesChange {

    private final PlanRepository plans;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the reads and the transactions.
     *
     * @param plans where plans are kept, and which have unchecked routes
     * @param clock the time, for the audit record
     * @param auditLog the audit trail, written in each change's transaction (TS §6.2)
     */
    public RecheckRoutesChange(PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The plans that have a route <em>not yet checked</em>.
     *
     * <p>Built in M9.
     *
     * @return the plans, possibly none
     */
    @Transactional(readOnly = true)
    public List<PlanId> due() {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * Reads one plan as it is, for its evaluation before the transaction.
     *
     * <p>Built in M9.
     *
     * @param plan the plan
     * @return the plan's date, the version read and its layout
     * @throws NotFound when no plan has the ID
     */
    @Transactional(readOnly = true)
    public LayoutToJudge layoutOf(PlanId plan) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * Records one plan's check.
     *
     * <p>Built in M9.
     *
     * @param plan the plan
     * @param planVersion the version {@link #layoutOf} read
     * @param evaluation the validator's judgement of the plan as it was read, or the one {@code
     *     FeasibilityChecker.unavailableFor} gave when the validator or the road network couldn't be
     *     reached
     * @param call the job's server step
     * @return the plan, for the dispatchers' open screens
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code planVersion}; the job leaves it for the
     *     next run
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code StaleEvaluation} when the
     *     evaluation describes another layout; the job leaves the plan for the next run
     */
    public PlanId apply(PlanId plan, Version planVersion, Evaluation evaluation, CallContext call) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * A plan as it is, with its date and the version it was read at, for the evaluation request.
     *
     * @param date the plan's delivery date
     * @param planVersion the version the layout was read from
     * @param layout the plan's routes, drops and pins
     */
    public record LayoutToJudge(DeliveryDate date, Version planVersion, PlanLayout layout) {

        /** Checks every part is present. */
        public LayoutToJudge {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(planVersion, "planVersion");
            Objects.requireNonNull(layout, "layout");
        }
    }
}
