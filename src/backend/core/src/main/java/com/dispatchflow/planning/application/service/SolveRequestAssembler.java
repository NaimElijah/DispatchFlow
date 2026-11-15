package com.dispatchflow.planning.application.service;

import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.orders.application.api.ServiceAreaCheck;
import com.dispatchflow.planning.application.port.out.RoadNetworkUnavailable;
import com.dispatchflow.planning.application.port.out.SolveConfiguration;
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
 * Gathers what the optimizer is asked, and translates every other module's answer into Planning's
 * own values on the way (TS §2.1, DL §2.3 rule 1, DL §6.3): the date's orders from Customers &amp;
 * Orders' {@code OrdersForDate}, the fleet and roster from Fleet's {@code FleetFacts}, what
 * happened on the routes from Live Operations through Planning's {@code
 * ExecutionFacts.liveStateOf}, asked with the plan's own routes and stops, each position's
 * service-area flag from {@code ServiceAreaCheck} (ADR-050), the settings, the configured values of
 * {@link SolveConfiguration}, and the matrix. The optimizer adapter then turns each request into
 * {@code optimizer-api}'s, so no outside model reaches Planning (TS §2.1 rule 4).
 *
 * <p>Each {@code ProblemFacts.Order} copies its {@code OrderFact}'s location and service-area flag
 * as they are, so an order outside the service area drops as <em>unreachable location</em>, unless
 * its location was placed by hand (FR-320).
 *
 * <p>A daily solve builds its matrix here, which is its job's <em>building matrix</em> phase
 * (BR-75): it reuses the plan's stored matrix when that was built over the same locations, found by
 * the list's hash through {@code StoredMatrices.matching}, and otherwise asks {@code
 * TravelTimes.between} for a new one (ADR-052). It only reads: {@code RunSolveJob} stores the
 * request's matrix in the job's success transaction.
 *
 * <p>A re-optimization and an evaluation reuse the plan's stored matrix while it has every location
 * they need. When it lacks one, such as a new order's address in a re-optimization (FR-35), or a
 * late order's on an edit check or the publishing re-check, they ask {@code TravelTimes.between}
 * for the whole matrix again over the new list of locations (ADR-121, which amends ADR-052's "a
 * re-optimization adds rows only"). A re-optimization's new matrix replaces the stored one in its
 * job's success transaction; an evaluation runs in no job and stores none. A re-optimization then
 * adds the position rows: one {@code TravelTimes.from} call, from each vehicle's latest position,
 * one row exactly where a position exists, to every location of its matrix, depots included
 * (ADR-050, ADR-052). Which positions are usable is the optimizer's to judge (BR-80, ADR-050). The
 * assembler reads every fact outside any transaction (DL §6.3).
 *
 * <p>Every request's {@code ProblemFacts} carries the door-window rate and the break flag, and a
 * re-optimization's request adds the lateness rate and the moved-stop cost, all from {@link
 * SolveConfiguration} (ADR-029, ADR-121). The departure allowance and a handed-over route's
 * confirmed return go to the optimizer as they are, and the optimizer applies them: the assembler
 * never folds them into the shift times (ADR-050, ADR-121). Reporting's baseline request is
 * Reporting's: its {@code BaselineRequest} is built on Reporting's side, and not here.
 *
 * <p>The plan's locks are not sent until the optimizer has a lock rule; until then {@code
 * RunSolveJob} checks the validated result with {@code Plan.locksBrokenBy} (ADR-112, ADR-121).
 *
 * <p>A daily request's vehicles are those rostered to an available driver, active and not marked
 * unavailable, because the optimizer requires a driver for every daily vehicle (ADR-105). It relies
 * on Fleet's {@code FleetFacts.rosterOn} keeping only drivers whose standing is active (ADR-062),
 * so it checks no standing itself. An evaluation's vehicles are every vehicle its layout routes,
 * each rostered to the layout's driver for that route, whatever the vehicle's state and the
 * driver's availability, because the optimizer requires a driver for every vehicle. So an
 * evaluation never refuses a problem for BR-20: the calling use case checks the roster itself (see
 * {@code PublishPlan} and {@code HandOverRoute}).
 *
 * <p>Built in M0, M4 and M9, method by method.
 */
public final class SolveRequestAssembler {

    private final PlanRepository plans;
    private final SolveJobRepository jobs;
    private final PlanningSettingsRepository settings;
    private final SolveConfiguration configuration;
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
     * @param configuration the configured rates and break flag (BR-115, BR-120, FR-81, ADR-121)
     * @param storedMatrices each plan's stored matrix (ADR-052)
     * @param travelTimes the road network's travel times, for a new matrix and a re-optimization's
     *     position rows (ADR-052, ADR-121)
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
            SolveConfiguration configuration,
            StoredMatrices storedMatrices,
            TravelTimes travelTimes,
            FleetFacts fleetFacts,
            OrdersForDate ordersForDate,
            ServiceAreaCheck serviceArea,
            ExecutionFacts executionFacts) {
        this.plans = plans;
        this.jobs = jobs;
        this.settings = settings;
        this.configuration = configuration;
        this.storedMatrices = storedMatrices;
        this.travelTimes = travelTimes;
        this.fleetFacts = fleetFacts;
        this.ordersForDate = ordersForDate;
        this.serviceArea = serviceArea;
        this.executionFacts = executionFacts;
    }

    /**
     * The daily solve a claimed job runs: the date's uncancelled orders, the vehicles rostered to
     * an available driver, the rostered drivers, the settings, the plan's pins, its drops a
     * dispatcher took off a route, the door-window rate and the break flag, and the job's solver,
     * limit and seed (FR-15, FR-19, FR-20, FR-91, FR-146, NFR-13, BR-120, FR-81). It builds or
     * reuses the matrix, and stores none (ADR-052). It carries the plan's locked routes, which the
     * adapter doesn't send until the optimizer has a lock rule (FR-147, ADR-112, ADR-121).
     *
     * <p>Built in M0; in full in M2 and M3. M0 reads the seeded orders, fleet and roster, because
     * Fleet's and Customers &amp; Orders' facts arrive in M2. {@code TravelTimes} and {@code
     * StoredMatrices} arrive in M3, so where M0's matrix comes from until then is M0's choice; no
     * document settles it.
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
     * The re-optimization a claimed job runs: the routes of the plan's current revision, what
     * happened on each up to {@code factsAt} from {@code ExecutionFacts.liveStateOf} with the
     * plan's own routes and stops, each vehicle's state and last two positions with their
     * service-area flags, each handed-over route's confirmed return, the new and put-back orders,
     * the four configured values, the matrix, and the position rows from {@code TravelTimes.from}
     * (FR-35, FR-394, BR-115, BR-120, ADR-050, ADR-052). The matrix is the stored one when that has
     * every location, and otherwise a new one over the new list, from {@code TravelTimes.between}
     * (ADR-121).
     *
     * <p>It sends every vehicle of the plan, whatever its state, and every order on the current
     * revision's routes besides the date's orders, so every stop has its order and its vehicle, one
     * put back to another date included. Its put-back orders are those on the routes whose failed
     * stop a dispatcher put back for today (FR-75): the date's orders hold the order at a later
     * attempt than its failed stop's.
     *
     * <p>Built in M9.
     *
     * @param date the plan's delivery date
     * @param job the claimed job
     * @param factsAt the time the facts are gathered at, which becomes the proposal's fact time
     *     (BR-25)
     * @return the request
     * @throws NotFound when no job has the ID
     * @throws RoadNetworkUnavailable when the road network can't be reached
     */
    public ReoptimizationRequest reoptimization(DeliveryDate date, JobId job, Instant factsAt) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * The evaluation of a layout of the date's plan, against the date's facts now, the door-window
     * rate, the break flag and the plan's stored matrix (FR-23, FR-233, BR-21, FR-395, BR-120,
     * FR-81). When the stored matrix lacks a location, such as a late order's, it asks {@code
     * TravelTimes.between} for a new one over the new list, and stores none (ADR-121).
     *
     * <p>It sends every vehicle the layout routes, whatever its state, each rostered to the
     * layout's driver for that route, with that driver's shift on the date's roster, whatever their
     * availability (ADR-105). So a hand-over is judged with the new driver's shift (ADR-112), and
     * BR-20's roster check is left to the calling use case.
     *
     * <p><b>Gap until M9.</b> It builds the daily evaluation only: no reported stop, no position
     * and no {@code now}. Whether evaluating a published plan mid-day takes the daily or the
     * re-optimization rules is open, and M9 decides it (see {@code FeasibilityChecker}).
     *
     * <p><b>Open for M4.</b> A layout driver with no shift on the date's roster has none to send.
     * A solve or a hand-over never leaves one, but a later roster change can, and so can a driver
     * who retired or lost the Driver role since, whom {@code FleetFacts.rosterOn} leaves out. The
     * evaluation reads the driver's shift through {@code FleetFacts.driverOn}, which reports the
     * standing; M4 decides what it sends for a driver with no shift.
     *
     * <p>Built in M4.
     *
     * @param date the plan's delivery date
     * @param layout the plan as it is, or as a change would leave it
     * @param planVersion the version the layout was read from
     * @return the request
     * @throws NotFound when the date has no uncancelled plan
     * @throws RoadNetworkUnavailable when the stored matrix lacks a location and the road network
     *     can't be reached; the caller then asks {@code FeasibilityChecker.unavailableFor} for the
     *     layout's version
     */
    public EvaluationRequest evaluation(DeliveryDate date, PlanLayout layout, Version planVersion) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
