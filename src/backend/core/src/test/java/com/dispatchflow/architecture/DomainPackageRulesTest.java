package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.archfixture.alpha.application.AlphaUseCase;
import com.dispatchflow.archfixture.epsilon.domain.LayerBelow;
import com.dispatchflow.archfixture.epsilon.domain.RootInACycle;
import com.dispatchflow.archfixture.epsilon.domain.RootUsingASubpackage;
import com.dispatchflow.archfixture.epsilon.domain.cycle.SubpackageInACycle;
import com.dispatchflow.archfixture.epsilon.domain.layer.LayerAbove;
import com.dispatchflow.archfixture.epsilon.domain.unmarked.UnmarkedValue;
import com.dispatchflow.archfixture.epsilon.domain.used.UsedByTheRoot;
import com.dispatchflow.archfixture.shared.KernelValue;
import com.tngtech.archunit.library.dependencies.Slices;
import org.junit.jupiter.api.Test;

/**
 * Proves each package rule of ADR-118 fires on the fixture that breaks it and allows the clean
 * ones.
 */
class DomainPackageRulesTest {

    private static final String FIXTURE_ROOT = FixtureClasses.ROOT;
    private static final String ALPHA_DOMAIN = FIXTURE_ROOT + ".alpha.domain";

    @Test
    void GivenADomainRootAndASubpackageThatUseEachOther_WhenTheCycleRuleEvaluates_ThenItIsFlagged() {
        assertThat(DomainPackageRules.domainPackagesAreFreeOfCycles(FIXTURE_ROOT)
                        .evaluate(FixtureClasses.only(RootInACycle.class, SubpackageInACycle.class))
                        .hasViolation())
                .isTrue();
    }

    @Test
    void GivenASubpackageThatUsesTheDomainRootOneWay_WhenTheCycleRuleEvaluates_ThenNothingIsFlagged() {
        assertThat(DomainPackageRules.domainPackagesAreFreeOfCycles(FIXTURE_ROOT)
                        .evaluate(FixtureClasses.only(LayerBelow.class, LayerAbove.class))
                        .hasViolation())
                .isFalse();
    }

    @Test
    void GivenTheSplitAlphaDomain_WhenTheCycleRuleEvaluates_ThenNothingIsFlagged() {
        assertThat(DomainPackageRules.domainPackagesAreFreeOfCycles(FIXTURE_ROOT)
                        .evaluate(FixtureClasses.inPackage("alpha"))
                        .hasViolation())
                .isFalse();
    }

    @Test
    void GivenADomainRootThatUsesASubpackageOneWay_WhenTheCycleRuleEvaluates_ThenNothingIsFlagged() {
        assertThat(DomainPackageRules.domainPackagesAreFreeOfCycles(FIXTURE_ROOT)
                        .evaluate(FixtureClasses.only(RootUsingASubpackage.class, UsedByTheRoot.class))
                        .hasViolation())
                .isFalse();
    }

    @Test
    void GivenADomainRootThatUsesASubpackageOneWay_WhenTheRootRuleEvaluates_ThenItIsFlagged() {
        assertThat(DomainPackageRules.domainRootsUseNoSubpackage(FIXTURE_ROOT)
                        .evaluate(FixtureClasses.only(RootUsingASubpackage.class, UsedByTheRoot.class))
                        .hasViolation())
                .isTrue();
    }

    @Test
    void GivenADomainRootInACycleWithASubpackage_WhenTheRootRuleEvaluates_ThenItIsFlagged() {
        assertThat(DomainPackageRules.domainRootsUseNoSubpackage(FIXTURE_ROOT)
                        .evaluate(FixtureClasses.only(RootInACycle.class, SubpackageInACycle.class))
                        .hasViolation())
                .isTrue();
    }

    @Test
    void GivenASubpackageThatUsesTheDomainRootOneWay_WhenTheRootRuleEvaluates_ThenNothingIsFlagged() {
        assertThat(DomainPackageRules.domainRootsUseNoSubpackage(FIXTURE_ROOT)
                        .evaluate(FixtureClasses.only(LayerBelow.class, LayerAbove.class))
                        .hasViolation())
                .isFalse();
    }

    @Test
    void GivenTheSplitAlphaDomain_WhenTheRootRuleEvaluates_ThenNothingIsFlagged() {
        assertThat(DomainPackageRules.domainRootsUseNoSubpackage(FIXTURE_ROOT)
                        .evaluate(FixtureClasses.inPackage("alpha"))
                        .hasViolation())
                .isFalse();
    }

    @Test
    void GivenTheSplitAlphaModule_WhenSliced_ThenEachDomainPackageIsASliceAndNothingElseIs() {
        Slices slices = Slices.assignedFrom(DomainPackageRules.domainPackages(FIXTURE_ROOT))
                .transform(FixtureClasses.inPackage("alpha"));

        assertThat(slices.stream()
                        .map(slice -> slice.iterator().next().getPackageName())
                        .toList())
                .containsExactlyInAnyOrder(
                        ALPHA_DOMAIN,
                        ALPHA_DOMAIN + ".clock",
                        ALPHA_DOMAIN + ".dependency",
                        ALPHA_DOMAIN + ".error",
                        ALPHA_DOMAIN + ".exception",
                        ALPHA_DOMAIN + ".id",
                        ALPHA_DOMAIN + ".io",
                        ALPHA_DOMAIN + ".logging",
                        ALPHA_DOMAIN + ".personal",
                        ALPHA_DOMAIN + ".randomness",
                        ALPHA_DOMAIN + ".shape");
    }

    @Test
    void GivenADomainSubpackageWithNoPackageInfo_WhenTheNullMarkedGuardChecks_ThenItIsReported() {
        assertThat(DomainPackageRules.packagesNotNullMarked(FIXTURE_ROOT, FixtureClasses.only(UnmarkedValue.class)))
                .containsExactly(FIXTURE_ROOT + ".epsilon.domain.unmarked");
    }

    @Test
    void GivenDomainAndEventsPackageInfosWithoutNullMarked_WhenTheNullMarkedGuardChecks_ThenBothAreReported() {
        assertThat(DomainPackageRules.packagesNotNullMarked(FIXTURE_ROOT, FixtureClasses.inPackage("gamma")))
                .containsExactly(FIXTURE_ROOT + ".gamma.domain", FIXTURE_ROOT + ".gamma.events");
    }

    @Test
    void GivenAKernelWithNoPackageInfo_WhenTheNullMarkedGuardChecks_ThenItIsReported() {
        assertThat(DomainPackageRules.packagesNotNullMarked(FIXTURE_ROOT, FixtureClasses.only(KernelValue.class)))
                .containsExactly(FIXTURE_ROOT + ".shared");
    }

    @Test
    void GivenADomainRootAndSubpackagesThatAreNullMarked_WhenTheNullMarkedGuardChecks_ThenNoneIsReported() {
        assertThat(DomainPackageRules.packagesNotNullMarked(
                        FIXTURE_ROOT,
                        FixtureClasses.only(
                                RootInACycle.class, SubpackageInACycle.class, LayerBelow.class, LayerAbove.class)))
                .isEmpty();
    }

    @Test
    void GivenAnApplicationPackageWithNoPackageInfo_WhenTheNullMarkedGuardChecks_ThenItIsNotChecked() {
        assertThat(DomainPackageRules.nullMarkedSubjects(FIXTURE_ROOT, FixtureClasses.only(AlphaUseCase.class)))
                .isEmpty();
    }
}
