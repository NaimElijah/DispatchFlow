package com.dispatchflow.planning.application.service;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * The midnight job, at 00:00 {@code Asia/Jerusalem} after a date: it first fails every queued or
 * running solve job of the date with the reason <em>its date passed</em>, then completes the date's
 * published plan, or abandons the plan still unpublished (BR-102, BR-127, BR-128, DL §9.1, §12,
 * ADR-053). Each job and the plan change in a transaction of their own (DL §6.1).
 *
 * <p>A job that ended meanwhile refuses with {@code JobAlreadyEnded}, and the job moves on to the next
 * (DL §11). It doesn't retry a save the version check refuses: it throws {@link StaleVersion}, and the
 * midnight job's adapter calls it again, which reloads the plan, up to three times, and then fails
 * loudly for the team's alert (DL §11, §12).
 *
 * <p>It loops over the jobs {@link CloseDeliveryDateChange#unfinishedJobsOn} lists, and {@link
 * CloseDeliveryDateChange} holds each job's transaction and then the plan's; after each commits, the
 * dispatchers' open screens are told (ADR-129).
 *
 * <p>It lives in the service root because it changes two aggregates, the jobs and the plan.
 *
 * <p>Built in M5.
 */
public final class CloseDeliveryDate {

    private final LiveUpdates liveUpdates;
    private final CloseDeliveryDateChange change;

    /**
     * Creates the job.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change each job's and the plan's transaction
     */
    public CloseDeliveryDate(LiveUpdates liveUpdates, CloseDeliveryDateChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
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
     * @throws StaleVersion when the plan changed between its read and its save; the midnight job's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void close(DeliveryDate date, CallContext call) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
