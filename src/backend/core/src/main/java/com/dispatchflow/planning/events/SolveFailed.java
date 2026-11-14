package com.dispatchflow.planning.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import java.time.Instant;

/**
 * A solve job ended without a result (DL §7.2). A daily job's plan returns to draft or ready
 * (BR-59); a failed re-optimization changes nothing (BR-26).
 *
 * @param eventId the event's ID
 * @param occurredAt when the job ended
 * @param job the job
 * @param plan the plan it solved
 * @param lane the lane it ran in
 * @param reason why it failed (FR-65)
 */
public record SolveFailed(
        EventId eventId, Instant occurredAt, JobId job, PlanId plan, Lane lane, SolveFailureReason reason)
        implements DomainEvent {}
