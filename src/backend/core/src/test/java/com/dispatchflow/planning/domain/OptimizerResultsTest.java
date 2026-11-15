package com.dispatchflow.planning.domain;

import static com.dispatchflow.planning.domain.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.PlanningFixtures.at;
import static com.dispatchflow.planning.domain.PlanningFixtures.routePlan;
import static com.dispatchflow.planning.domain.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.PlanningFixtures.uuid;
import static com.dispatchflow.planning.domain.PlanningFixtures.violation;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.planning.domain.PlanningError.SolutionRejected;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OptimizerResultsTest {

    private static final RouteId ROUTE = RouteId.of(uuid(42));

    @Test
    void GivenAnAcceptedReport_WhenItsSolutionIsTaken_ThenItHoldsTheReportsRoutesAndDrops() {
        ValidationReport report = ValidationReport.of(List.of(), List.of(solved(V1, D1, O1)), List.of());

        ValidatedSolution solution = ValidatedSolution.of(report);

        assertThat(report.accepted()).isTrue();
        assertThat(solution.routes()).isEqualTo(report.routes());
        assertThat(solution.dropped()).isEmpty();
    }

    @Test
    void GivenAReportWithViolations_WhenItsSolutionIsTaken_ThenSolutionRejected() {
        ValidationReport report = ValidationReport.of(
                List.of(violation("BR-05", ROUTE), violation("BR-06", ROUTE)), List.of(solved(V1, D1, O1)), List.of());

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
                        new DropReason.TimeWindow(at(30_000), at(31_000)),
                        new DropReason.Shift(at(60_000), at(61_000)),
                        new DropReason.DepotHours(at(60_000), at(61_000)),
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
