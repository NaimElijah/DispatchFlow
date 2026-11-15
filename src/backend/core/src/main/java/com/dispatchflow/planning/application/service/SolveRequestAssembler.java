package com.dispatchflow.planning.application.service;

import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.orders.application.api.ServiceAreaCheck;
import com.dispatchflow.planning.application.port.out.RoadNetworkUnavailable;
import com.dispatchflow.planning.application.port.out.StoredMatrices;
import com.dispatchflow.planning.application.port.out.TravelTimes;
import com.dispatchflow.planning.application.port.out.plan.EvaluationRequest;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.settings.PlanningSettingsRepository;
import com.dispatchflow.planning.application.port.out.solvejob.ReoptimizationRequest;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveRequest;
import com.dispatchflow.planning.application.spi.ExecutionFacts;
import com.dispatchflow.planning.domain.plan.PlanLayout;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import java.time.Instant;

/**
 * Gathers what the optimizer is asked, and translates every other module's answer into Planning's own
 * values on the way (TS §2.1, DL §2.3 rule 1, DL §6.3): the date's orders from Customers &amp; Orders'
 * {@code OrdersForDate}, the fleet and roster from Fleet's {@code FleetFacts}, what happened on the
 * routes from Live Operations through Planning's {@code ExecutionFacts.liveStateOf}, asked with the
 * plan's own routes and stops, each position's service-area flag from {@code ServiceAreaCheck}
 * (ADR-050), the settings, and the matrix. The optimizer adapter then turns each request into {@code
 * optimizer-api}'s, so no outside model reaches Planning (TS §2.1 rule 4).
 *
 * <p>Each {@code ProblemFacts.Order} copies its {@code OrderFact}'s location and service-area flag
 * as they are, so an order outside the service area drops as <em>unreachable location</em>, unless
 * its pin is fixed (FR-320).
 *
 * <p>A daily solve builds its matrix here, which is its job's <em>building matrix</em> phase (BR-75):
 * it reuses the plan's stored matrix when the locations are the same, and otherwise asks {@code
 * TravelTimes} for a new one (ADR-052). It only reads: {@code RunSolveJob} stores the request's
 * matrix in the job's success transaction. A re-optimization reuses the stored matrix and adds rows
 * only: one {@code TravelTimes.from} call, from each vehicle's latest position, one row exactly where
 * a position exists, to every location of the stored matrix, depots included (ADR-050, ADR-052).
 * Which positions are usable is the optimizer's to judge (BR-80, ADR-050). New locations, such as a
 * new order's address, wait for the owner's decision on extending the matrix. An evaluation reuses
 * the stored matrix. It reads every fact outside any transaction (DL §6.3).
 *
 * <p>A daily request's vehicles are those rostered to an available driver, active and not marked
 * unavailable, because the optimizer requires a driver for every daily vehicle (ADR-105). It relies
 * on Fleet's {@code FleetFacts.rosterOn} keeping only drivers whose standing is active (ADR-062), so
 * it checks no standing itself.
 *
 * <p>Built in M0, M4 and M9, method by method.
 */
public final class SolveRequestAssembler {

    private final PlanRepository plans;
    private final SolveJobRepository jobs;
    private final PlanningSettingsRepository settings;
    private final StoredMatrices storedMatrices;
    private final TravelTimes travelTimes;
    private final FleetFacts fleetFacts;
    private final OrdersForDate ordersForDate;
    private final ServiceAreaCheck serviceArea;
    private final ExecutionFacts executionFacts;

    /**
     * Creates the assembler.
     *
     * @param plans where plans are kept, for the pins, locks and routes a request carries
     * @param jobs where solve jobs are kept, for each job's solver, limit, seed and plan
     * @param settings the planning settings, for the travel model (FR-321, FR-227, FR-21, FR-67)
     * @param storedMatrices each plan's stored matrix (ADR-052)
     * @param travelTimes the road network's travel times, for a new matrix and a re-optimization's
     *     position rows (ADR-052)
     * @param fleetFacts Fleet's register and roster
     * @param ordersForDate Customers &amp; Orders' orders of a date
     * @param serviceArea Customers &amp; Orders' service area, for each position's flag (ADR-050)
     * @param executionFacts Live Operations' facts about the plan's own routes and stops, for a
     *     re-optimization
     */
    public SolveRequestAssembler(
            PlanRepository plans,
            SolveJobRepository jobs,
            PlanningSettingsRepository settings,
            StoredMatrices storedMatrices,
            TravelTimes travelTimes,
            FleetFacts fleetFacts,
            OrdersForDate ordersForDate,
            ServiceAreaCheck serviceArea,
            ExecutionFacts executionFacts) {
        this.plans = plans;
        this.jobs = jobs;
        this.settings = settings;
        this.storedMatrices = storedMatrices;
        this.travelTimes = travelTimes;
        this.fleetFacts = fleetFacts;
        this.ordersForDate = ordersForDate;
        this.serviceArea = serviceArea;
        this.executionFacts = executionFacts;
    }

    /**
     * The daily solve a claimed job runs: the date's uncancelled orders, the vehicles rostered to an
     * available driver, the rostered drivers, the settings, the plan's pins, its drops a dispatcher
     * took off a route, its locked routes, and the job's solver, limit and seed (FR-15, FR-19, FR-20,
     * FR-91, FR-146, FR-147, NFR-13). It builds or reuses the matrix, and stores none (ADR-052).
     *
     * <p>Built in M0 with seeded facts, in full in M2 and M3: M0 reads the seeded orders, fleet and
     * roster, because Fleet's and Customers &amp; Orders' facts arrive in M2. {@code TravelTimes} and
     * {@code StoredMatrices} arrive in M3, so where M0's matrix comes from until then is M0's choice;
     * no document settles it.
     *
     * @param date the plan's delivery date
     * @param job the claimed job
     * @return the request
     * @throws NotFound when no job has the ID
     * @throws RoadNetworkUnavailable when the road network can't be reached, which fails the job as
     *     <em>road network unavailable</em> (FR-65)
     */
    public SolveRequest daily(DeliveryDate date, JobId job) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * The re-optimization a claimed job runs: the routes of the plan's current revision, what happened
     * on each up to {@code factsAt} from {@code ExecutionFacts.liveStateOf} with the plan's own routes
     * and stops, each vehicle's state and last two positions with their service-area flags, the new
     * and put-back orders, the stored matrix, and the position rows from {@code TravelTimes.from}
     * (FR-35, ADR-050, ADR-052).
     *
     * <p>It sends every vehicle of the plan, whatever its state, and every order on the current
     * revision's routes besides the date's orders, so every stop has its order and its vehicle, one put
     * back to another date included. Its put-back orders are those on the routes whose failed stop a
     * dispatcher put back for today (FR-75): the date's orders hold the order at a later attempt than
     * its failed stop's.
     *
     * <p>Built in M9.
     *
     * @param date the plan's delivery date
     * @param job the claimed job
     * @param factsAt the time the facts are gathered at, which becomes the proposal's fact time (BR-25)
     * @return the request
     * @throws NotFound when no job has the ID
     * @throws RoadNetworkUnavailable when the road network can't be reached
     */
    public ReoptimizationRequest reoptimization(DeliveryDate date, JobId job, Instant factsAt) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * The evaluation of a layout of the date's plan, against the date's facts now and the plan's
     * stored matrix (FR-23, FR-233, BR-21, FR-395). Each route's vehicle is rostered to the layout's
     * driver for it, so a hand-over is judged with the new driver's shift (ADR-112).
     *
     * <p><b>Gap until M9.</b> It builds the daily evaluation only: no reported stop, no position and
     * no {@code now}. Whether evaluating a published plan mid-day takes the daily or the
     * re-optimization rules is open, and M9 decides it (see {@code FeasibilityChecker}).
     *
     * <p>Built in M4.
     *
     * @param date the plan's delivery date
     * @param layout the plan as it is, or as a change would leave it
     * @param planVersion the version the layout was read from
     * @return the request
     * @throws NotFound when the date has no uncancelled plan
     */
    public EvaluationRequest evaluation(DeliveryDate date, PlanLayout layout, Version planVersion) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
