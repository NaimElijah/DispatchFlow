package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.Seeds;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.settings.PlanningSettingsRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RequestSolve}: it loads the plan, checks its version, reads the daily
 * lane's capacity under its lock, makes the plan solving with {@code Plan.requestSolve}, saves the
 * plan, adds the new job and writes the audit record (DL §6.1, TS §6.2, ADR-063, ADR-129).
 *
 * <p>It saves the plan before it adds the job: adding the job writes at once everything the
 * transaction holds, so the plan's change written there would make its own save fail.
 *
 * <p>Built in M0.
 */
@Transactional
public class RequestSolveChange {

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
     * @param jobs where solve jobs are kept, and the daily lane's capacity under its lock
     * @param settings the planning settings, for the default solver and the default limit
     * @param seeds the new job's seed (NFR-13)
     * @param clock the time, for {@code today} and {@code now}
     * @param ids the new job's ID
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public RequestSolveChange(
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
     * Requests the solve, with the solver already checked against the optimizer's list.
     *
     * <p>Built in M0.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param solver the solver, or empty for the settings' daily default (FR-20, ADR-004)
     * @param limit the search limit, or empty for the settings' default (FR-19)
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @return the new job's ID
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotDraftOrReady},
     *     {@code DateInPast} (BR-118), or {@code SolveQueueFull} when the daily lane holds 5 queued
     *     jobs (BR-112)
     */
    public JobId apply(
            Actor actor, PlanId plan, Optional<SolverName> solver, Optional<SolveLimit> limit, Version expected) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
