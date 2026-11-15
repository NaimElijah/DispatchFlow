package com.dispatchflow.planning.domain.solution;

import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.at;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dayAt;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.routePlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.uuid;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.violation;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.planning.domain.PlanningError.SolutionRejected;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OptimizerResultsTest {

    private static final RouteId ROUTE = RouteId.of(uuid(42));

    @Test
    void GivenAnAcceptedReport_WhenItsSolutionIsTaken_ThenItHoldsTheReportsRoutesAndDrops() {
        ValidationReport report = ValidationReport.of(List.of(), List.of(solved(V1, D1, O1)), List.of(), List.of());

        ValidatedSolution solution = ValidatedSolution.of(report);

        assertThat(report.accepted()).isTrue();
        assertThat(solution.routes()).isEqualTo(report.routes());
        assertThat(solution.dropped()).isEmpty();
    }

    @Test
    void GivenAReportWithViolations_WhenItsSolutionIsTaken_ThenSolutionRejected() {
        ValidationReport report = ValidationReport.of(
                List.of(violation("BR-05", ROUTE), violation("BR-06", ROUTE)),
                List.of(solved(V1, D1, O1)),
                List.of(),
                List.of());

        assertRefused(() -> ValidatedSolution.of(report), new SolutionRejected(2));
    }

    @Test
    void GivenABr06ViolationOnARoute_WhenTheEvaluationIsAsked_ThenItBreaksThatRoutesShiftOnly() {
        RouteId other = RouteId.of(uuid(43));
        Evaluation evaluation = Evaluation.checked(
                Version.NEW,
                List.of(violation("BR-06", ROUTE), violation("BR-105", other)),
                List.of(new RouteTimes(ROUTE, V1, D1, routePlan(60_000), List.of())));

        assertThat(evaluation.breaksShift(ROUTE)).isTrue();
        assertThat(evaluation.breaksShift(other)).isFalse();
        assertThat(evaluation.returnOf(ROUTE)).contains(at(60_000));
        assertThat(evaluation.returnOf(other)).isEmpty();
    }

    @Test
    void GivenALateShift_WhenADropsEarliestReturnPassesTheDay_ThenItKeepsTheSecondsFromTheDaysStart() {
        DropReason.Shift shift = new DropReason.Shift(dayAt(84_600), at(88_200));

        assertThat(shift.earliestReturn().seconds()).isEqualTo(88_200);
        assertThat(shift.earliestReturn().pastTheDay()).isTrue();
    }

    @Test
    void GivenARouteTheValidatorCouldntTime_WhenItsReturnIsAsked_ThenThereIsNone() {
        Evaluation evaluation = Evaluation.checked(
                Version.NEW, List.of(), List.of(new RouteTimes(ROUTE, V1, D1, Optional.empty(), List.of())));

        assertThat(evaluation.returnOf(ROUTE)).isEmpty();
    }

    @Test
    void GivenAReportThatLeftVehiclesOut_WhenItsSolutionIsTaken_ThenItNamesEachWithItsCause() {
        List<ExcludedVehicle> excluded = List.of(
                new ExcludedVehicle(V2, ExclusionCause.DEPOT_WITHOUT_LOCATION),
                new ExcludedVehicle(V3, ExclusionCause.NO_ROAD_BETWEEN_DEPOTS));
        ValidationReport report = ValidationReport.of(List.of(), List.of(solved(V1, D1, O1)), List.of(), excluded);

        assertThat(report.excluded()).isEqualTo(excluded);
        assertThat(ValidatedSolution.of(report).excluded()).isEqualTo(excluded);
    }

    @Test
    void GivenOrphanedAndMovedStops_WhenTheRouteStabilityIsRead_ThenItIsTheirSum() {
        Duration none = Duration.ZERO;
        ObjectiveTerms terms = new ObjectiveTerms(
                Duration.ofSeconds(3_600),
                Duration.ofSeconds(10_000),
                none,
                none,
                Duration.ofSeconds(120),
                Duration.ofSeconds(600),
                Duration.ofSeconds(300));

        assertThat(terms.routeStability()).isEqualTo(Duration.ofSeconds(900));
    }

    @Test
    void GivenAnUnreachableOptimizer_WhenAnEvaluationSaysSo_ThenItJudgedNothing() {
        Evaluation evaluation = Evaluation.unavailable(Version.NEW);

        assertThat(evaluation.available()).isFalse();
        assertThat(evaluation.violations()).isEmpty();
        assertThat(evaluation.routes()).isEmpty();
    }

    @Test
    void GivenEachDropReason_WhenAskedWhetherItIsAboutTheLocation_ThenOnlyTheTwoLocationReasonsAre() {
        Weight kilograms = Weight.kilograms(BigDecimal.TEN);
        Volume litres = Volume.litres(BigDecimal.TEN);
        Map<Class<?>, DropReason> samples = new LinkedHashMap<>();
        Stream.of(
                        new DropReason.MissingLocation(),
                        new DropReason.NoVehicleAvailable(),
                        new DropReason.UnreachableLocation(),
                        new DropReason.RemovedByDispatcher(),
                        new DropReason.PinNotHonoured(new PinTarget.ToVehicle(V1), false),
                        new DropReason.Capacity(kilograms, litres, kilograms, litres),
                        new DropReason.TimeWindow(dayAt(30_000), at(31_000)),
                        new DropReason.Shift(dayAt(60_000), at(61_000)),
                        new DropReason.DepotHours(dayAt(60_000), at(61_000)),
                        new DropReason.VehicleLimit(),
                        new DropReason.Displaced())
                .forEach(reason -> samples.put(reason.getClass(), reason));
        Set<Class<?>> locationReasons = Set.of(DropReason.MissingLocation.class, DropReason.UnreachableLocation.class);

        assertThat(samples.keySet()).containsExactlyInAnyOrder(DropReason.class.getPermittedSubclasses());
        for (Class<?> variant : DropReason.class.getPermittedSubclasses()) {
            assertThat(samples.get(variant).isAboutLocation())
                    .as(variant.getSimpleName())
                    .isEqualTo(locationReasons.contains(variant));
        }
    }
}
