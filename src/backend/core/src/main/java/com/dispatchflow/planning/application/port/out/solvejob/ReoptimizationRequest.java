package com.dispatchflow.planning.application.port.out.solvejob;

import com.dispatchflow.planning.application.port.out.ProblemFacts;
import com.dispatchflow.planning.application.port.out.TravelRows;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.plan.PlanLayout;
import com.dispatchflow.planning.domain.plan.Seed;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.planning.domain.solution.DroppedOrder;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A re-optimization of a published plan's remaining stops, as the optimizer is asked for it: the raw
 * facts only, because the Python {@code ProblemFactory.reoptimization} applies every convention
 * (FR-35, BR-84, ADR-050). {@code SolveRequestAssembler.reoptimization} builds it.
 *
 * <p>Built in M9.
 *
 * @param job the job
 * @param plan the plan
 * @param basis the plan revision it starts from (BR-25, BR-82)
 * @param solver the solver (FR-20)
 * @param limit the search limit, 20 seconds (BR-84)
 * @param seed the seed (NFR-13)
 * @param now the time the facts were gathered, the proposal's fact time (BR-25)
 * @param facts the date's orders, new and put-back ones included, and every order on the current
 *     revision's routes; every vehicle of the plan; the roster, the settings and the stored matrix
 * @param routes the routes of the plan's current revision, each with what happened on it
 * @param dropped the plan's dropped orders
 * @param putBack the orders on {@code routes} whose failed stop a dispatcher put back for today
 *     (FR-75), which the adapter sends as {@code put_back}
 * @param positionRows the base travel times and distances from each vehicle's latest position, one
 *     row exactly where a position exists, to every location of the stored matrix, depots included:
 *     the extra source rows ADR-050 and ADR-052 ask for. Whether a position is usable is the
 *     optimizer's to judge (BR-80, ADR-050), and the stored matrix itself isn't rebuilt
 */
public record ReoptimizationRequest(
        JobId job,
        PlanId plan,
        RevisionNo basis,
        SolverName solver,
        SolveLimit limit,
        Seed seed,
        Instant now,
        ProblemFacts facts,
        List<LiveRoute> routes,
        List<DroppedOrder> dropped,
        List<OrderId> putBack,
        TravelRows positionRows) {

    /** Checks every part is present, and copies the lists. */
    public ReoptimizationRequest {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(solver, "solver");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(facts, "facts");
        routes = List.copyOf(routes);
        dropped = List.copyOf(dropped);
        putBack = List.copyOf(putBack);
        Objects.requireNonNull(positionRows, "positionRows");
    }

    /**
     * Where a route's vehicle stands for the re-optimization (ADR-050 point 3).
     *
     * <p>Built in M9.
     */
    public enum VehicleState {
        /** Driving, or about to. */
        ACTIVE,
        /** Marked unavailable since the plan was published (FR-300). */
        UNAVAILABLE,
        /** Its driver is no longer rostered or available. */
        NO_DRIVER,
        /** Its driver's shift is over (BR-06, FR-394). */
        OUT_OF_SHIFT,
        /** Its driver finished the route (FR-329). */
        FINISHED,
        /** Withdrawn mid-route by an earlier re-optimization (FR-267). */
        WITHDRAWN
    }

    /**
     * What stands at a reported stop: in progress, delivered or failed (BR-10, BR-91).
     *
     * <p>Built in M9.
     */
    public enum ReportedState {
        /** The driver arrived, with no outcome yet. */
        ARRIVED,
        /** Delivered. */
        DELIVERED,
        /** Failed. */
        FAILED
    }

    /**
     * One route of the current revision and what happened on it (ADR-050 points 1 to 3).
     *
     * <p>Built in M9.
     *
     * @param route the route as the revision holds it
     * @param state where its vehicle stands
     * @param confirmedReturn the return time a confirmed hand-over set, which the optimizer may not
     *     stretch (FR-394)
     * @param reported its reported stops, in the order reported
     * @param lastPositions its vehicle's last two positions, the latest last (BR-79, BR-80)
     */
    public record LiveRoute(
            PlanLayout.LaidOutRoute route,
            VehicleState state,
            Optional<SolveTime> confirmedReturn,
            List<ReportedStop> reported,
            List<Position> lastPositions) {

        /** Checks every part is present, possibly empty, and copies the lists. */
        public LiveRoute {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(confirmedReturn, "confirmedReturn");
            reported = List.copyOf(reported);
            lastPositions = List.copyOf(lastPositions);
        }
    }

    /**
     * One reported stop, with its corrected times (BR-10, BR-91, BR-114). The adapter sends the
     * optimizer's {@code reported_at}, the corrected time of the stop's first report, from them: its
     * arrival when recorded, otherwise its outcome.
     *
     * <p>Built in M9.
     *
     * @param stop the stop
     * @param progress what stands at it
     * @param arrivedAt the corrected time of the first report of its arrival (BR-114), if recorded
     * @param outcomeAt the corrected time of the first report of its outcome (BR-114), if it has one
     */
    public record ReportedStop(
            StopId stop, ReportedState progress, Optional<Instant> arrivedAt, Optional<Instant> outcomeAt) {

        /** Checks every part is present, possibly empty. */
        public ReportedStop {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(progress, "progress");
            Objects.requireNonNull(arrivedAt, "arrivedAt");
            Objects.requireNonNull(outcomeAt, "outcomeAt");
        }
    }

    /**
     * One position of a vehicle, with the server's receive time and whether it lies in the service
     * area, which the core computes (ADR-050, FR-320). A driver's place is personal data, so it prints
     * redacted.
     *
     * <p>Built in M9.
     *
     * @param at where the phone was
     * @param receivedAt when the server received it
     * @param inServiceArea whether the service area contains it
     */
    public record Position(GeoPoint at, Instant receivedAt, boolean inServiceArea) implements PersonalData {

        /** Checks both parts are present. */
        public Position {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(receivedAt, "receivedAt");
        }

        /**
         * A form that hides the place.
         *
         * @return {@code Position[redacted]}
         */
        @Override
        public String toString() {
            return "Position[redacted]";
        }
    }
}
