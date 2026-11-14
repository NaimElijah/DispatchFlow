package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;

/** The purity rules of DL §3.9 and §13, checked against the production classes. */
class DomainPurityTest {

    private static final String ROOT = ProductionClasses.ROOT;

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
    void GivenTheProductionClasses_WhenRule6IsChecked_ThenOnlyPersistenceRestores() {
        DomainPurityRules.restoreOnlyFromPersistence(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenTheOptimizerResultRuleIsChecked_ThenOnlyTheOptimizerAdapterBuildsThem() {
        DomainPurityRules.optimizerResultsOnlyFromOptimizerAdapter(ROOT).check(ProductionClasses.ALL);
    }
}
