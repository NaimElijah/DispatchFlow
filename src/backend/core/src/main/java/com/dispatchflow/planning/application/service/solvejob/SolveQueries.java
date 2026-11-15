package com.dispatchflow.planning.application.service.solvejob;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.planning.application.port.out.SolveProgressView;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;

/**
 * The solve's progress, which the dispatcher's screen reads when the event stream says a job changed
 * (FR-16, FR-65, FR-142, FR-222). It is a dispatcher screen: a job belongs to a plan that a driver
 * never sees before it is published (BR-41).
 *
 * <p>Built in M4.
 */
public final class SolveQueries {

    private final PlanningViews views;
    private final Clock clock;

    /**
     * Creates the queries.
     *
     * @param views Planning's read side
     * @param clock the server's time, which the answer carries
     */
    public SolveQueries(PlanningViews views, Clock clock) {
        this.views = views;
        this.clock = clock;
    }

    /**
     * Where a job is now.
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @param job the job
     * @return its lane, solver, limit, phase with its times, and the server's time
     * @throws NotFound when no job has the ID
     */
    public SolveProgressView progress(Actor actor, JobId job) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
