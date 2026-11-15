package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
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

/**
 * Requests a daily solve of a draft or ready plan: the plan becomes solving, and a job joins the daily
 * lane in the same transaction, read under the lane's lock (FR-15, FR-16, FR-19, FR-20, FR-92,
 * FR-224, FR-225, BR-112, DL §6.1, §6.2). Solving again discards the plan's manual edits, which the
 * screen warns about first (FR-92). With no solver chosen it runs {@link SolverName#OR_TOOLS}
 * (placement P3), and with no limit the settings' default (FR-19, FR-183).
 *
 * <p>Built in M0.
 */
public final class RequestSolve {

    private final PlanRepository plans;
    private final SolveJobRepository jobs;
    private final PlanningSettingsRepository settings;
    private final Seeds seeds;
    private final Clock clock;
    private final IdGenerator ids;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept
     * @param jobs where solve jobs are kept, and the daily lane's capacity under its lock
     * @param settings the planning settings, for the default limit
     * @param seeds the new job's seed (NFR-13)
     * @param clock the time, for {@code today} and {@code now}
     * @param ids the new job's ID
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public RequestSolve(
            PlanRepository plans,
            SolveJobRepository jobs,
            PlanningSettingsRepository settings,
            Seeds seeds,
            Clock clock,
            IdGenerator ids,
            LiveUpdates liveUpdates,
            AuditLog auditLog) {
        this.plans = plans;
        this.jobs = jobs;
        this.settings = settings;
        this.seeds = seeds;
        this.clock = clock;
        this.ids = ids;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Requests the solve.
     *
     * <p>Built in M0.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param solver the solver, or empty for the default (FR-20)
     * @param limit the search limit, or empty for the settings' default (FR-19)
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @return the new job's ID
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotDraftOrReady}, {@code
     *     DateInPast} (BR-118), or {@code SolveQueueFull} when the daily lane holds 5 queued jobs
     *     (BR-112)
     */
    public JobId request(
            Actor actor, PlanId plan, Optional<SolverName> solver, Optional<SolveLimit> limit, Version expected) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
