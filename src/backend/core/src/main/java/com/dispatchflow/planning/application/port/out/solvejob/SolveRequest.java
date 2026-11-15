package com.dispatchflow.planning.application.port.out.solvejob;

import com.dispatchflow.planning.application.port.out.ProblemFacts;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.plan.Pin;
import com.dispatchflow.planning.domain.plan.PlanLayout;
import com.dispatchflow.planning.domain.plan.RouteLock;
import com.dispatchflow.planning.domain.plan.Seed;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import java.util.List;
import java.util.Objects;

/**
 * A daily solve, as the optimizer is asked for it (FR-15, FR-17, FR-19, FR-20, FR-91, FR-147,
 * NFR-13, ADR-044): the job's solver, limit and seed, the date's facts, the plan's pins, the orders a
 * dispatcher took off, and the routes a lock keeps. {@code SolveRequestAssembler.daily} builds it.
 *
 * <p>The optimizer has no lock rule yet, so the adapter doesn't send {@code locks} until it has one
 * (ADR-112, ADR-121). Until then {@code RunSolveJob} checks the validated result with {@code
 * Plan.locksBrokenBy}, and a broken lock fails the job (FR-238).
 *
 * <p>Built in M0; its locked routes in an unscheduled milestone, with {@code LockRoute}.
 *
 * @param job the job
 * @param plan the plan it solves
 * @param solver the solver (FR-20)
 * @param limit the search limit, which runs from the start of the search (FR-19, BR-75)
 * @param seed the seed (NFR-13)
 * @param facts the date's orders, fleet, roster, settings and matrix
 * @param pins the plan's pins (FR-91)
 * @param removedByDispatcher the plan's dropped orders a dispatcher took off a route (FR-146), which
 *     the adapter sends as {@code removed_by_dispatcher}, so a drop keeps that reason; the solve may
 *     serve them again (BR-95)
 * @param locks the plan's locked routes, which the solve must keep (FR-147, FR-238); not sent
 *     until the optimizer has a lock rule (ADR-112, ADR-121)
 */
public record SolveRequest(
        JobId job,
        PlanId plan,
        SolverName solver,
        SolveLimit limit,
        Seed seed,
        ProblemFacts facts,
        List<Pin> pins,
        List<OrderId> removedByDispatcher,
        List<LockedRoute> locks) {

    /** Checks every part is present, and copies the lists. */
    public SolveRequest {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(solver, "solver");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(facts, "facts");
        pins = List.copyOf(pins);
        removedByDispatcher = List.copyOf(removedByDispatcher);
        locks = List.copyOf(locks);
    }

    /**
     * One locked route and what its lock keeps: the whole route, or the sequence of its stops
     * (FR-147, ADR-101).
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param route the route as the plan holds it
     * @param lock the kind of lock
     */
    public record LockedRoute(PlanLayout.LaidOutRoute route, RouteLock lock) {

        /** Checks both parts are present. */
        public LockedRoute {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(lock, "lock");
        }
    }
}
