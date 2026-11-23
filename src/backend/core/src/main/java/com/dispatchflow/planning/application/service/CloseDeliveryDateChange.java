package com.dispatchflow.planning.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link CloseDeliveryDate}: a read-only one that lists the date's unfinished
 * jobs, one per job that fails it because its date passed, and one that completes or abandons the
 * date's plan, each saving its aggregate and writing its audit record (DL §6.1, §9.1, TS §6.2,
 * ADR-053, ADR-129).
 *
 * <p>Built in M5.
 */
@Transactional
public class CloseDeliveryDateChange {

    private final SolveJobRepository jobs;
    private final PlanRepository plans;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param jobs where solve jobs are kept
     * @param plans where plans are kept
     * @param clock the time, for {@code today} and {@code now}
     * @param auditLog the audit trail, written in each change's transaction (TS §6.2)
     */
    public CloseDeliveryDateChange(SolveJobRepository jobs, PlanRepository plans, Clock clock, AuditLog auditLog) {
        this.jobs = jobs;
        this.plans = plans;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The date's jobs still queued, building their matrix or searching.
     *
     * <p>Built in M5.
     *
     * @param date the date that just ended
     * @return the jobs, possibly none
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code DateNotOver} when called
     *     before the midnight after the date
     */
    @Transactional(readOnly = true)
    public List<JobId> unfinishedJobsOn(DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * Fails one job with the reason <em>its date passed</em> (BR-102, ADR-053).
     *
     * <p>Built in M5.
     *
     * @param job the job
     * @param call the job's server step
     * @return the job's plan, for the dispatchers' open screens
     * @throws NotFound when no job has the ID
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code JobAlreadyEnded} when the
     *     job ended meanwhile; the midnight job moves on to the next (DL §11)
     */
    public PlanId failJob(JobId job, CallContext call) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * Completes the date's published plan, or abandons the plan still unpublished (BR-127, BR-128).
     *
     * <p>Built in M5.
     *
     * @param date the date that just ended
     * @param call the job's server step
     * @return the plan it closed, for the dispatchers' open screens, or empty when the date has no
     *     unfinished plan
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code DateNotOver} when called
     *     before the midnight after the date
     * @throws StaleVersion when the plan changed between its read and its save; the midnight job's
     *     adapter retries at once, up to three times (DL §11)
     */
    public Optional<PlanId> closePlan(DeliveryDate date, CallContext call) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
