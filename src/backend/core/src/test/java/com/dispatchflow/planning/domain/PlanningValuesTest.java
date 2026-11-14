package com.dispatchflow.planning.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.domain.PlanningError.InvalidAttempt;
import com.dispatchflow.planning.domain.PlanningError.InvalidSolverName;
import com.dispatchflow.planning.domain.PlanningError.InvalidStopTimes;
import com.dispatchflow.planning.domain.PlanningError.InvalidTravelLeg;
import com.dispatchflow.planning.domain.PlanningError.MalformedRuleCode;
import com.dispatchflow.planning.domain.PlanningError.SolveLimitOutOfRange;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlanningValuesTest {

    private static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2027, 6, 15));
    private static final DeliveryDate NEXT_DATE = DeliveryDate.of(LocalDate.of(2027, 6, 16));

    @Test
    void GivenOneOrMore_WhenAnAttemptIsBuilt_ThenItIsKept() {
        assertThat(Attempt.of(1).number()).isEqualTo(1);
        assertThat(Attempt.FIRST.next()).isEqualTo(Attempt.of(2));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void GivenANumberBelowOne_WhenAnAttemptIsBuilt_ThenInvalidAttempt(int number) {
        assertRefused(() -> Attempt.of(number), new InvalidAttempt(number));
    }

    @Test
    void GivenTheFirstRevision_WhenTheNextIsTaken_ThenItIsTwo() {
        assertThat(RevisionNo.FIRST.next()).isEqualTo(RevisionNo.of(2));
    }

    @Test
    void GivenAStoredRevisionBelowOne_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> RevisionNo.of(0)).isInstanceOf(StoredStateInvalid.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 15, 30})
    void GivenAnIndexFromOneToThirty_WhenBuilt_ThenItIsKept(int index) {
        assertThat(RouteIndex.of(index).value()).isEqualTo(index);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31, -1})
    void GivenAStoredIndexOutsideOneToThirty_WhenRebuilt_ThenStoredStateInvalid(int index) {
        assertThatThrownBy(() -> RouteIndex.of(index)).isInstanceOf(StoredStateInvalid.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BR-03", "FR-91", "NFR-168", "BR-1000"})
    void GivenARequirementRowId_WhenARuleCodeIsBuilt_ThenItIsKeptAsGiven(String code) {
        assertThat(RuleCode.of(code).value()).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "br-03", "BR03", "BR-", "B-03", "BR-03 ", "BR-12345", "ABCD-1"})
    void GivenTextThatIsNotARowId_WhenARuleCodeIsBuilt_ThenMalformedRuleCode(String code) {
        assertRefused(() -> RuleCode.of(code), new MalformedRuleCode());
    }

    @Test
    void GivenTheShiftRule_WhenItsCodeIsRead_ThenItIsBr06() {
        assertThat(RuleCode.SHIFTS).isEqualTo(RuleCode.of("BR-06"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ortools", "alns", "nearest_neighbour", "a"})
    void GivenALowerCaseName_WhenASolverNameIsBuilt_ThenItIsKept(String name) {
        assertThat(SolverName.of(name).value()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "OR-Tools", "1solver", "or tools", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void GivenTextThatIsNotASolverName_WhenBuilt_ThenInvalidSolverName(String name) {
        assertRefused(() -> SolverName.of(name), new InvalidSolverName());
    }

    @Test
    void GivenNoChoice_WhenTheDefaultSolverIsRead_ThenItIsOrTools() {
        assertThat(SolverName.OR_TOOLS).isEqualTo(SolverName.of("ortools"));
    }

    @ParameterizedTest
    @ValueSource(longs = {5, 45, 60})
    void GivenFiveToSixtySeconds_WhenADailyLimitIsBuilt_ThenItIsKept(long seconds) {
        assertThat(SolveLimit.daily(Duration.ofSeconds(seconds)).duration()).isEqualTo(Duration.ofSeconds(seconds));
    }

    @ParameterizedTest
    @ValueSource(longs = {4, 61, 0, -5})
    void GivenSecondsOutsideFiveToSixty_WhenADailyLimitIsBuilt_ThenSolveLimitOutOfRange(long seconds) {
        assertRefused(() -> SolveLimit.daily(Duration.ofSeconds(seconds)), new SolveLimitOutOfRange(seconds));
    }

    @Test
    void GivenAFractionOfASecond_WhenADailyLimitIsBuilt_ThenSolveLimitOutOfRange() {
        assertRefused(() -> SolveLimit.daily(Duration.ofMillis(10_500)), new SolveLimitOutOfRange(10));
    }

    @Test
    void GivenTheRequirements_WhenTheNamedLimitsAreRead_ThenTheyAreFortyFiveAndTwenty() {
        assertThat(SolveLimit.DEFAULT_DAILY.duration()).isEqualTo(Duration.ofSeconds(45));
        assertThat(SolveLimit.REOPTIMIZATION.duration()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void GivenTimesInOrder_WhenStopTimesAreBuilt_ThenTheyAreKept() {
        StopTimes times = StopTimes.of(at(100), at(100), at(400));

        assertThat(times.start()).isEqualTo(at(100));
    }

    @Test
    void GivenAStartBeforeTheArrival_WhenStopTimesAreBuilt_ThenInvalidStopTimes() {
        assertRefused(() -> StopTimes.of(at(200), at(100), at(400)), new InvalidStopTimes());
    }

    @Test
    void GivenADepartureBeforeTheStart_WhenStopTimesAreBuilt_ThenInvalidStopTimes() {
        assertRefused(() -> StopTimes.of(at(100), at(300), at(200)), new InvalidStopTimes());
    }

    @Test
    void GivenTimesOnTwoDates_WhenStopTimesAreBuilt_ThenInvalidStopTimes() {
        assertRefused(() -> StopTimes.of(at(100), at(100), DaySeconds.of(NEXT_DATE, 200)), new InvalidStopTimes());
    }

    @Test
    void GivenZeroSecondsAndMetres_WhenALegIsBuilt_ThenItIsKept() {
        assertThat(TravelLeg.of(Duration.ZERO, 0).metres()).isZero();
    }

    @Test
    void GivenNegativeSeconds_WhenALegIsBuilt_ThenInvalidTravelLeg() {
        assertRefused(() -> TravelLeg.of(Duration.ofSeconds(-1), 10), new InvalidTravelLeg());
    }

    @Test
    void GivenNegativeMetres_WhenALegIsBuilt_ThenInvalidTravelLeg() {
        assertRefused(() -> TravelLeg.of(Duration.ofSeconds(1), -10), new InvalidTravelLeg());
    }

    private static DaySeconds at(long seconds) {
        return DaySeconds.of(DATE, seconds);
    }
}
