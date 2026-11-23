package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.Seeds;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.settings.PlanningSettingsRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RequestReoptimization}: it loads the plan, reads the re-optimization
 * lane's capacity under its lock, queues the job with {@code Plan.requestReoptimization}, adds it and
 * writes the audit record (TS §6.2, ADR-063, ADR-101, ADR-129).
 *
 * <p>Built in M9.
 */
@Transactional
public class RequestReoptimizationChange {

    private final PlanRepository plans;
    private final SolveJobRepository jobs;
    private final PlanningSettingsRepository settings;
    private final Seeds seeds;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept
     * @param jobs where solve jobs are kept, and the re-optimization lane's capacity under its lock
     * @param settings the planning settings, for the default solver
     * @param seeds the new job's seed (NFR-13)
     * @param clock the time, for {@code today} and {@code now}
     * @param ids the new job's ID
     * @param auditLog the audit trail, written in the job's transaction (TS §6.2)
     */
    public RequestReoptimizationChange(
            PlanRepository plans,
            SolveJobRepository jobs,
            PlanningSettingsRepository settings,
            Seeds seeds,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.plans = plans;
        this.jobs = jobs;
        this.settings = settings;
        this.seeds = seeds;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Queues the re-optimization, with the solver already checked against the optimizer's list.
     *
     * <p>Built in M9.
     *
     * @param actor the dispatcher
     * @param plan the published plan
     * @param solver the solver, or empty for the settings' re-optimization default (FR-20, ADR-004)
     * @return the new job's ID
     * @throws NotFound when no plan has the ID
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished},
     *     {@code DateInPast} (BR-118), {@code ReoptimizationAlreadyRunning} (BR-38), or {@code
     *     SolveQueueFull} when the lane holds 5 queued jobs (BR-112)
     */
    public JobId apply(Actor actor, PlanId plan, Optional<SolverName> solver) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
