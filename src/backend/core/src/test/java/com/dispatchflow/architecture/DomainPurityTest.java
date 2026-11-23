package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The purity rules of DL §3.9 and §13, checked against the production classes. Rule 6 and the
 * optimizer-result rule act only on the types their predicates pick, so the guards below fail when
 * a predicate picks no production type, which would let its rule pass on nothing.
 */
class DomainPurityTest {

    private static final String ROOT = ProductionClasses.ROOT;

    private static Stream<String> picked(Predicate<JavaClass> predicate) {
        return ProductionClasses.ALL.stream().filter(predicate).map(JavaClass::getSimpleName);
    }

    @Test
    void GivenTheProductionClasses_WhenAggregatesArePicked_ThenEachModulesAggregatesAreAmongThem() {
        assertThat(picked(RestrictedTypes::isAggregate)).contains("Plan", "Order", "Delivery", "Session");
    }

    @Test
    void GivenTheProductionClasses_WhenSnapshotsReadUnderALockArePicked_ThenTheyAreTheThreeSnapshots() {
        assertThat(picked(RestrictedTypes::readsUnderLock))
                .containsExactlyInAnyOrder("ActiveAdmins", "DateCapacity", "LaneCapacity");
    }

    @Test
    void GivenTheProductionClasses_WhenOptimizerResultsArePicked_ThenTheReportAndTheEvaluationAreAmongThem() {
        assertThat(picked(type -> RestrictedTypes.optimizerResultOf(type)
                        .filter(result -> result.equals(type))
                        .isPresent()))
                .containsExactlyInAnyOrder("ValidationReport", "Evaluation");
    }

    @Test
    void GivenTheProductionImport_WhenListed_ThenItHoldsTheKernelAndNoFixture() {
        assertThat(ProductionClasses.ALL.stream().map(JavaClass::getPackageName))
                .contains("com.dispatchflow.shared")
                .noneMatch(name -> name.startsWith("com.dispatchflow.archfixture"));
    }

    @Test
    void GivenTheProductionClasses_WhenRule1IsChecked_ThenTheyDependOnlyOnWhatIsAllowed() {
        DomainPurityRules.onlyAllowedDependencies(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule2IsChecked_ThenNoneReadsTheClock() {
        DomainPurityRules.noSystemClock(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule3IsChecked_ThenNoneGeneratesAnId() {
        DomainPurityRules.noIdGeneration(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule4IsCheckedForRandomness_ThenNoneUsesIt() {
        DomainPurityRules.noRandomness(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule4IsCheckedForIo_ThenNoneDoesIt() {
        DomainPurityRules.noIo(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule4IsCheckedForLogging_ThenNoneLogs() {
        DomainPurityRules.noLogging(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule5IsChecked_ThenNoneCreatesAGenericException() {
        DomainPurityRules.noGenericExceptions(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule6IsChecked_ThenOnlyPersistenceReadsUnderALock() {
        DomainPurityRules.lockReadsOnlyFromPersistence(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenTheOptimizerResultRuleIsChecked_ThenOnlyTheOptimizerAdapterBuildsThem() {
        DomainPurityRules.optimizerResultsOnlyFromOptimizerAdapter(ROOT).check(ProductionClasses.ALL);
    }
}
