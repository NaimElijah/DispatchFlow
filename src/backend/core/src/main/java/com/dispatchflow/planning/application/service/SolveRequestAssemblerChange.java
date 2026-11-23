package com.dispatchflow.planning.application.service;

import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.orders.application.api.ServiceAreaCheck;
import com.dispatchflow.planning.application.port.out.SolveConfiguration;
import com.dispatchflow.planning.application.port.out.StoredMatrices;
import com.dispatchflow.planning.application.port.out.TravelMatrix;
import com.dispatchflow.planning.application.port.out.TravelRows;
import com.dispatchflow.planning.application.port.out.plan.EvaluationRequest;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.settings.PlanningSettingsRepository;
import com.dispatchflow.planning.application.port.out.solvejob.ReoptimizationRequest;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.planning.application.port.out.solvejob.SolveRequest;
import com.dispatchflow.planning.application.spi.ExecutionFacts;
import com.dispatchflow.planning.domain.plan.PlanLayout;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only transactions of {@link SolveRequestAssembler}. Each kind of request has two: a read
 * of which locations its matrix must span, and whether the plan's stored matrix serves, before the
 * assembler asks the road network; then a read of the facts the request carries, from Planning's
 * own records and from the other modules' answers, which builds the request over the matrix the
 * assembler fetched, or over the stored one (TS §2.1, DL §6.3, ADR-052, ADR-129). It stores
 * nothing.
 *
 * <p><b>Open for M0.</b> An order added, moved or cancelled between the two reads can leave the
 * fetched matrix without a location the build needs, or with one it no longer does. M0 decides
 * whether the build then reads the need again or refuses.
 *
 * <p>Built in M0, M4 and M9, method by method.
 */
@Transactional(readOnly = true)
public class SolveRequestAssemblerChange {

    private final PlanRepository plans;
    private final SolveJobRepository jobs;
    private final PlanningSettingsRepository settings;
    private final SolveConfiguration configuration;
    private final StoredMatrices storedMatrices;
    private final FleetFacts fleetFacts;
    private final OrdersForDate ordersForDate;
    private final ServiceAreaCheck serviceArea;
    private final ExecutionFacts executionFacts;

    /**
     * Creates the transaction.
     *
     * @param plans where plans are kept, for the pins, locks and routes a request carries
     * @param jobs where solve jobs are kept, for each job's solver, limit, seed and plan
     * @param settings the planning settings, for the travel model (FR-321, FR-227, FR-21, FR-67)
     * @param configuration the configured rates and break flag (BR-115, BR-120, FR-81, ADR-121)
     * @param storedMatrices each plan's stored matrix (ADR-052)
     * @param fleetFacts Fleet's register and roster
     * @param ordersForDate Customers &amp; Orders' orders of a date
     * @param serviceArea Customers &amp; Orders' service area, for each position's flag (ADR-050)
     * @param executionFacts Live Operations' facts about the plan's own routes and stops, for a
     *     re-optimization
     */
    public SolveRequestAssemblerChange(
            PlanRepository plans,
            SolveJobRepository jobs,
            PlanningSettingsRepository settings,
            SolveConfiguration configuration,
            StoredMatrices storedMatrices,
            FleetFacts fleetFacts,
            OrdersForDate ordersForDate,
            ServiceAreaCheck serviceArea,
            ExecutionFacts executionFacts) {
        this.plans = plans;
        this.jobs = jobs;
        this.settings = settings;
        this.configuration = configuration;
        this.storedMatrices = storedMatrices;
        this.fleetFacts = fleetFacts;
        this.ordersForDate = ordersForDate;
        this.serviceArea = serviceArea;
        this.executionFacts = executionFacts;
    }

    /**
     * Reads which locations the daily solve's matrix must span, and whether the plan's stored matrix
     * was built over exactly those, found by the list's hash (ADR-052).
     *
     * <p>Built in M0.
     *
     * @param date the plan's delivery date
     * @param job the claimed job
     * @return the locations, and whether the stored matrix serves; no positions
     * @throws NotFound when no job has the ID
     */
    @Transactional(readOnly = true)
    public MatrixNeed dailyMatrixNeed(DeliveryDate date, JobId job) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Reads which locations the re-optimization's matrix must span, whether the plan's stored matrix
     * has every one, and each vehicle's latest position up to {@code factsAt}, for the position rows
     * (ADR-050, ADR-052, ADR-121).
     *
     * <p>Built in M9.
     *
     * @param date the plan's delivery date
     * @param job the claimed job
     * @param factsAt the time the facts are gathered at (BR-25)
     * @return the locations, whether the stored matrix serves, and the positions
     * @throws NotFound when no job has the ID
     */
    @Transactional(readOnly = true)
    public MatrixNeed reoptimizationMatrixNeed(DeliveryDate date, JobId job, Instant factsAt) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * Reads which locations an evaluation of the layout must span, and whether the plan's stored
     * matrix has every one, such as a late order's (ADR-121).
     *
     * <p>Built in M4.
     *
     * @param date the plan's delivery date
     * @param layout the plan as it is, or as a change would leave it
     * @return the locations, and whether the stored matrix serves; no positions
     * @throws NotFound when the date has no uncancelled plan
     */
    @Transactional(readOnly = true)
    public MatrixNeed evaluationMatrixNeed(DeliveryDate date, PlanLayout layout) {
        throw new UnsupportedOperationException("Built in M4");
    }

    /**
     * Reads the daily solve's facts and builds its request (FR-15, FR-19, FR-20, FR-91, FR-146,
     * NFR-13, BR-120, FR-81).
     *
     * <p>Built in M0.
     *
     * @param date the plan's delivery date
     * @param job the claimed job
     * @param newMatrix the matrix {@code TravelTimes.between} gave, or empty to build over the plan's
     *     stored matrix when that was built over the same locations (ADR-052)
     * @return the request
     * @throws NotFound when no job has the ID
     */
    public SolveRequest daily(DeliveryDate date, JobId job, Optional<TravelMatrix> newMatrix) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Reads the re-optimization's facts up to {@code factsAt} and builds its request (FR-35, FR-394,
     * BR-115, BR-120, ADR-050, ADR-052).
     *
     * <p>Built in M9.
     *
     * @param date the plan's delivery date
     * @param job the claimed job
     * @param factsAt the time the facts are gathered at, which becomes the proposal's fact time
     *     (BR-25)
     * @param newMatrix the matrix {@code TravelTimes.between} gave over the new list of locations, or
     *     empty to build over the plan's stored matrix when that has every location (ADR-121)
     * @param positionRows the rows {@code TravelTimes.from} gave from each vehicle's latest position
     * @return the request
     * @throws NotFound when no job has the ID
     */
    public ReoptimizationRequest reoptimization(
            DeliveryDate date, JobId job, Instant factsAt, Optional<TravelMatrix> newMatrix, TravelRows positionRows) {
        throw new UnsupportedOperationException("Built in M9");
    }

    /**
     * Reads the date's facts now and builds the evaluation of a layout of the date's plan (FR-23,
     * FR-233, BR-21, FR-395, BR-120, FR-81).
     *
     * <p>Built in M4.
     *
     * @param date the plan's delivery date
     * @param layout the plan as it is, or as a change would leave it
     * @param planVersion the version the layout was read from
     * @param newMatrix the matrix {@code TravelTimes.between} gave over the new list of locations, or
     *     empty to build over the plan's stored matrix when that has every location (ADR-121)
     * @return the request
     * @throws NotFound when the date has no uncancelled plan
     */
    public EvaluationRequest evaluation(
            DeliveryDate date, PlanLayout layout, Version planVersion, Optional<TravelMatrix> newMatrix) {
        throw new UnsupportedOperationException("Built in M4");
    }

    /**
     * What the road network must be asked before a request is built. The locations are customers'
     * places and the positions vehicles' places, so it prints redacted.
     *
     * @param locations every location the request's matrix must span, depots included
     * @param storedMatrixServes whether the plan's stored matrix already spans them, so no new one
     *     is asked
     * @param positions each vehicle's latest position, the sources of a re-optimization's position
     *     rows; empty for a daily solve and an evaluation
     */
    public record MatrixNeed(List<GeoPoint> locations, boolean storedMatrixServes, List<GeoPoint> positions)
            implements PersonalData {

        /** Copies the lists. */
        public MatrixNeed {
            locations = List.copyOf(locations);
            positions = List.copyOf(positions);
        }

        /**
         * A form that hides the places.
         *
         * @return {@code MatrixNeed[redacted]}
         */
        @Override
        public String toString() {
            return "MatrixNeed[redacted]";
        }
    }
}
