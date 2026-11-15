package com.dispatchflow.planning.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.application.Clock;

/**
 * The midnight job, at 00:00 {@code Asia/Jerusalem} after a date: it first fails every queued or
 * running solve job of the date with the reason <em>its date passed</em>, then completes the date's
 * published plan, or abandons the plan still unpublished (BR-102, BR-127, BR-128, DL §9.1, §12,
 * ADR-053). Each job and the plan change in a transaction of their own (DL §6.1).
 *
 * <p>A job that ended meanwhile refuses with {@code JobAlreadyEnded}, and the job moves on to the next
 * (DL §11). A save the version check refuses reloads the plan and retries, up to three times, and then
 * fails loudly for the team's alert (DL §11, §12).
 *
 * <p>It lives in the service root because it changes two aggregates, the jobs and the plan.
 *
 * <p>Built in M5.
 */
public final class CloseDeliveryDate {

    private final SolveJobRepository jobs;
    private final PlanRepository plans;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param jobs where solve jobs are kept
     * @param plans where plans are kept
     * @param clock the time, for {@code today} and {@code now}
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in each change's transaction (TS §6.2)
     */
    public CloseDeliveryDate(
            SolveJobRepository jobs, PlanRepository plans, Clock clock, LiveUpdates liveUpdates, AuditLog auditLog) {
        this.jobs = jobs;
        this.plans = plans;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Closes a date that has ended.
     *
     * <p>Built in M5.
     *
     * @param date the date that just ended
     * @param call the job's server step
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code DateNotOver} when called
     *     before the midnight after the date
     * @throws com.dispatchflow.shared.application.StaleVersion when the plan's save is still refused
     *     after three retries
     */
    public void close(DeliveryDate date, CallContext call) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
