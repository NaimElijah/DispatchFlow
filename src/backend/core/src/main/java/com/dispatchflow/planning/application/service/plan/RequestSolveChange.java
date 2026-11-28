package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.planning.application.port.out.Seeds;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.settings.PlanningSettingsRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.plan.LaneCapacity;
import com.dispatchflow.planning.domain.plan.Plan;
import com.dispatchflow.planning.domain.plan.Seed;
import com.dispatchflow.planning.domain.plan.SolveJob;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RequestSolve}: it reads the daily lane's capacity under its lock, loads
 * the plan, checks its version, makes the plan solving with {@code Plan.requestSolve}, saves the
 * plan, adds the new job and writes the audit record of its success (DL §6.1, TS §6.2, ADR-063,
 * ADR-129).
 *
 * <p>It takes the lane's lock before it loads the plan, so two requests for one plan queue behind
 * each other and the second loads what the first committed: its version check then answers {@code
 * STALE_VERSION}. Loaded first, both would pass the check, and the second could be refused as
 * {@code SOLVE_QUEUE_FULL} by the first's job, though the plan is solving already.
 *
 * <p>It saves the plan before it adds the job: adding the job writes at once everything the
 * transaction holds, so the plan's change written there would make its own save fail.
 *
 * <p>{@code PlanningSettings} has no default solver yet (ADR-004), so a request with no solver runs
 * OR-Tools, the default FR-20 names.
 */
@Transactional
public class RequestSolveChange {

    private static final String KIND = "Plan";

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
     * @param settings the planning settings, for the default limit
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
     * Requests the solve, with the solver already checked by {@link RequestSolve}.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param solver the solver, or empty for OR-Tools (FR-20, ADR-004)
     * @param limit the search limit, or empty for the settings' default (FR-19)
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @return the new job's ID
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}, or since it was loaded
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotDraftOrReady},
     *     {@code DateInPast} (BR-118), or {@code SolveQueueFull} when the daily lane holds 5 queued
     *     jobs (BR-112)
     */
    public JobId apply(
            Actor actor, PlanId plan, Optional<SolverName> solver, Optional<SolveLimit> limit, Version expected) {
        LaneCapacity capacity = jobs.capacityUnderLock(Lane.DAILY);
        Plan loaded = plans.get(plan).orElseThrow(() -> new NotFound(KIND, plan.value()));
        StaleVersion.requireCurrent(KIND, plan.value(), loaded.version(), expected);
        SolveLimit searchLimit = limit.orElseGet(() -> settings.current().defaultSolveLimit());
        SolverName solverName = solver.orElse(SolverName.OR_TOOLS);
        Seed seed = seeds.next();
        JobId job = JobId.of(ids.next());
        Instant now = clock.now();
        SolveJob queued = loaded.requestSolve(job, solverName, searchLimit, seed, capacity, clock.today(), now);
        plans.save(loaded);
        jobs.add(queued);
        auditLog.succeeded(actor.call(), now, List.of(AuditedRecord.of(plan), AuditedRecord.of(job)));
        return job;
    }
}
