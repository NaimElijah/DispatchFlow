package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanAssertions.assertPlanRefused;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.NOW;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.freshIds;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.roomIn;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routeId;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.solved;
import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.domain.PlanningError.DriverAlreadyHasRoute;
import com.dispatchflow.planning.domain.PlanningError.OrderAlreadyInPlan;
import com.dispatchflow.planning.domain.PlanningError.RouteLocked;
import com.dispatchflow.planning.domain.PlanningError.VehicleAlreadyHasRoute;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.RouteId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A plan's own checks on a stored result it is given (BR-07, BR-39, FR-147): {@code
 * Plan.applySolveResult} refuses a result that serves an order twice or gives a vehicle or a driver
 * two routes, and checks its locks first. {@code SolveJob.succeed} refuses such a result, so no
 * command reaches one and these are backstops for a check skipped upstream (ADR-112).
 *
 * <p>So each test stores a valid result through the job's commands and damages its rows with SQL
 * into the result the plan must refuse, as a bug or a since-changed rule would leave it; the job's
 * own load check accepts it, since it checks no plan's rules. Only the job's rows are damaged, so
 * the plan is built and saved in memory. It sits beside {@code Plan}, the type it tests, and each
 * test keys its own job, because the database is shared and never cleaned.
 */
@IntegrationTest
class StoredResultAppliedIT {

    private static final long JOB_PREFIX = 0x0190a5e2_6261_636bL;

    @Autowired
    private SolveJobRepository jobs;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void GivenAStoredResultServingAnOrderTwice_WhenApplied_ThenOrderAlreadyInPlan() {
        Plan draft = aPlan().draft();
        JobId job = storeSucceeded(draft, 1, List.of(solved(V1, D1, O1), solved(V2, D2, O2)));
        damage(job, "solve_job_stop", "order_id = '" + O1.value() + "'", "route_position = 1");
        SolveJob stored = load(job);
        Plan plan = asSaved(draft);

        assertPlanRefused(
                plan, () -> plan.applySolveResult(stored, freshIds(0), LATER), new OrderAlreadyInPlan(O1, FIRST));
    }

    @Test
    void GivenAStoredResultGivingAVehicleTwoRoutes_WhenApplied_ThenVehicleAlreadyHasRoute() {
        Plan draft = aPlan().draft();
        JobId job = storeSucceeded(draft, 2, List.of(solved(V1, D1, O1), solved(V2, D2, O2)));
        damage(job, "solve_job_route", "vehicle_id = '" + V1.value() + "'", "position = 1");
        SolveJob stored = load(job);
        Plan plan = asSaved(draft);

        assertPlanRefused(
                plan,
                () -> plan.applySolveResult(stored, freshIds(0), LATER),
                new VehicleAlreadyHasRoute(V1, RouteId.of(PlanningFixtures.uuid(10_000 + 1))));
    }

    @Test
    void GivenAStoredResultGivingADriverTwoRoutes_WhenApplied_ThenDriverAlreadyHasRoute() {
        Plan draft = aPlan().draft();
        JobId job = storeSucceeded(draft, 3, List.of(solved(V1, D1, O1), solved(V2, D2, O2)));
        damage(job, "solve_job_route", "driver_id = '" + D1.value() + "'", "position = 1");
        SolveJob stored = load(job);
        Plan plan = asSaved(draft);

        assertPlanRefused(
                plan,
                () -> plan.applySolveResult(stored, freshIds(0), LATER),
                new DriverAlreadyHasRoute(D1, RouteId.of(PlanningFixtures.uuid(10_000 + 1))));
    }

    @Test
    void
            GivenAStoredResultThatChangesALockedRouteAndServesAnOrderTwice_WhenApplied_ThenTheLockIsRefusedFirstAsRouteLocked() {
        Plan ready = aPlan().ready();
        RouteId v2 = routeId(ready, V2);
        ready.lockRoute(v2, RouteLock.WHOLE_ROUTE, TODAY);
        Plan locked = asSaved(ready);
        JobId job = storeSucceeded(locked, 4, List.of(solved(V1, D1, O1), solved(V2, D2, O2, O3)));
        damage(job, "solve_job_stop", "order_id = '" + O1.value() + "'", "route_position = 1 AND stop_position = 0");
        SolveJob stored = load(job);
        Plan plan = asSaved(locked);

        assertPlanRefused(plan, () -> plan.applySolveResult(stored, freshIds(500), LATER), new RouteLocked(v2));
    }

    /**
     * The plan requests a daily solve, whose job succeeds with these routes, as its worker would leave
     * it, and is stored; the plan is then solving for it.
     */
    private JobId storeSucceeded(Plan plan, int n, List<SolvedRoute> routes) {
        JobId id = JobId.of(new UUID(JOB_PREFIX, n));
        SolveJob job = plan.requestSolve(
                id, SolverName.OR_TOOLS, SolveLimit.DEFAULT_DAILY, Seed.of(7), roomIn(Lane.DAILY), TODAY, NOW);
        job.claim(NOW);
        job.searchStarted(NOW);
        job.succeed(ValidatedSolution.of(ValidationReport.of(List.of(), routes, List.of(), List.of())), NOW);
        transactions.inTransaction(() -> jobs.add(job));
        return id;
    }

    /** Sets columns of the job's rows in one of its result tables; every statement is the test's own. */
    private void damage(JobId job, String table, String assignments, String condition) {
        jdbc.sql("UPDATE planning." + table + " SET " + assignments + " WHERE solve_job_id = ? AND (" + condition + ")")
                .param(job.value())
                .update();
    }

    private SolveJob load(JobId job) {
        return transactions.inTransaction(() -> jobs.get(job).orElseThrow());
    }
}
