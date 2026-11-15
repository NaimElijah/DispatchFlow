package com.dispatchflow.planning.events;

import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import java.time.Instant;
import java.util.Objects;

/**
 * A solve job ended with a validated result (DL §7.2). Planning's listener applies a daily result
 * to the plan; a re-optimization's result waits as a proposal.
 *
 * @param eventId the event's ID
 * @param occurredAt when the job ended
 * @param job the job
 * @param plan the plan it solved
 * @param lane the lane it ran in
 */
public record SolveSucceeded(EventId eventId, Instant occurredAt, JobId job, PlanId plan, Lane lane)
        implements DomainEvent {

    /** Checks every part is present. */
    public SolveSucceeded {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(lane, "lane");
    }
}
