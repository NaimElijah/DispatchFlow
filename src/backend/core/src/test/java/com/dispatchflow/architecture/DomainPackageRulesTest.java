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
import com.dispatchflow.archfixture.epsilon.events.below.BelowTheEvents;
import com.dispatchflow.archfixture.shared.KernelValue;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.library.dependencies.Slices;
import java.util.List;
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
                        ALPHA_DOMAIN + ".entity",
                        ALPHA_DOMAIN + ".error",
                        ALPHA_DOMAIN + ".error.personalvalue",
                        ALPHA_DOMAIN + ".error.text",
                        ALPHA_DOMAIN + ".exception",
                        ALPHA_DOMAIN + ".id",
                        ALPHA_DOMAIN + ".io",
                        ALPHA_DOMAIN + ".io.classpath",
                        ALPHA_DOMAIN + ".io.locale",
                        ALPHA_DOMAIN + ".io.process",
                        ALPHA_DOMAIN + ".io.thread",
                        ALPHA_DOMAIN + ".logging",
                        ALPHA_DOMAIN + ".mapping",
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

    @Test
    void GivenPackagesBelowAnApiAnSpiAnEventsAndTheKernel_WhenTheFlatGuardChecks_ThenExactlyThoseAreReported() {
        String root = "com.example";

        assertThat(DomainPackageRules.packagesBelowAFlatOne(
                        root,
                        List.of(
                                root,
                                root + ".shared",
                                root + ".shared.application",
                                root + ".shared.id",
                                root + ".shared.application.port",
                                root + ".shared.adapter.out.persistence",
                                root + ".shared.adapter.out.persistence.id",
                                root + ".shared.adapter.util",
                                root + ".alpha.events",
                                root + ".alpha.events.kind",
                                root + ".alpha.application.api",
                                root + ".alpha.application.api.plan",
                                root + ".alpha.application.spi",
                                root + ".alpha.application.spi.usage",
                                root + ".alpha.application.service.plan.edit",
                                root + ".alpha.application.service.api.x",
                                root + ".alpha.application.port.out.plan",
                                root + ".alpha.domain.plan",
                                root + ".alpha.domain.events.x",
                                root + ".web",
                                "org.other.alpha.events.kind")))
                .containsExactly(
                        root + ".alpha.application.api.plan",
                        root + ".alpha.application.spi.usage",
                        root + ".alpha.events.kind",
                        root + ".shared.adapter.util",
                        root + ".shared.application.port",
                        root + ".shared.id");
    }

    @Test
    void GivenTheKernelsAdapterPackages_WhenTheFlatGuardChecks_ThenOnlyThoseNamingNoAdapterAreReported() {
        String root = "com.example";

        assertThat(DomainPackageRules.packagesBelowAFlatOne(
                        root,
                        List.of(
                                root + ".shared.adapter",
                                root + ".shared.adapter.in",
                                root + ".shared.adapter.out",
                                root + ".shared.adapter.in.cli",
                                root + ".shared.adapter.out.clock",
                                root + ".shared.adapter.out.clock.system",
                                root + ".shared.adapter.out.uuid",
                                root + ".shared.adapter.util",
                                root + ".shared.adapter.util.text")))
                .containsExactly(
                        root + ".shared.adapter",
                        root + ".shared.adapter.in",
                        root + ".shared.adapter.out",
                        root + ".shared.adapter.util",
                        root + ".shared.adapter.util.text");
    }

    @Test
    void GivenAFixtureBelowAnEventsPackage_WhenTheFlatGuardChecksItsImportedClasses_ThenItsPackageIsReported() {
        List<String> packages = FixtureClasses.only(BelowTheEvents.class).stream()
                .map(JavaClass::getPackageName)
                .distinct()
                .toList();

        assertThat(DomainPackageRules.packagesBelowAFlatOne(FIXTURE_ROOT, packages))
                .containsExactly(FIXTURE_ROOT + ".epsilon.events.below");
    }
}
