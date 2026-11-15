package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.archfixture.modulith.NamedInterface;
import com.dispatchflow.archfixture.zeta.ZetaApplication;
import com.dispatchflow.archfixture.zeta.lower.adapter.out.LowerAdapterReachesUp;
import com.dispatchflow.archfixture.zeta.lower.application.api.ImplementedElsewhere;
import com.dispatchflow.archfixture.zeta.lower.application.api.ImplementedTwice;
import com.dispatchflow.archfixture.zeta.lower.application.api.LowerFacts;
import com.dispatchflow.archfixture.zeta.lower.application.api.LowerOutcome;
import com.dispatchflow.archfixture.zeta.lower.application.api.Unimplemented;
import com.dispatchflow.archfixture.zeta.lower.application.service.LowerReachesUp;
import com.dispatchflow.archfixture.zeta.lower.application.spi.LowerUsage;
import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageImplementedAtHome;
import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageUnimplemented;
import com.dispatchflow.archfixture.zeta.lower.events.LowerHappened;
import com.dispatchflow.archfixture.zeta.stray.StrayValue;
import com.dispatchflow.archfixture.zeta.upper.application.service.UpperUseCase;
import com.dispatchflow.archfixture.zeta.web.ZetaWebHandler;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ViolationHandler;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Proves each application rule fires on the fixtures under {@code archfixture.zeta}, which is the
 * rules' root here: its {@code lower} and {@code upper} are modules, {@code stray} is a top-level
 * package missing from the fixture graph, and {@code shared} and {@code web} are no modules. Every
 * test names the exact classes a rule flags, so a rule that flags too much fails as surely as one
 * that flags nothing.
 */
class ApplicationRulesTest {

    private static final String ZETA = "zeta";
    private static final String ROOT = FixtureClasses.ROOT + "." + ZETA;
    private static final String FIXTURE_MODULE_ANNOTATIONS = NamedInterface.class.getPackageName();
    // upper may depend on lower; lower on nothing. stray is left out on purpose.
    private static final Map<String, Set<String>> GRAPH = Map.of("lower", Set.of(), "upper", Set.of("lower"));

    private static JavaClasses zeta() {
        return FixtureClasses.inPackage(ZETA);
    }

    /** The simple names of the classes a rule reports a violation against. */
    private static Set<String> violators(ArchRule rule, JavaClasses classes) {
        Set<String> names = new TreeSet<>();
        ViolationHandler<JavaClass> collect =
                (violating, message) -> violating.forEach(type -> names.add(type.getSimpleName()));
        rule.evaluate(classes).handleViolations(collect);
        return names;
    }

    @Test
    void GivenTheZetaFixtures_WhenRule1Evaluates_ThenExactlyTheForbiddenDependenciesAreFlagged() {
        assertThat(violators(ApplicationRules.onlyAllowedDependencies(ROOT, FIXTURE_MODULE_ANNOTATIONS), zeta()))
                .containsExactlyInAnyOrder(
                        "SharedPortUsesSpring",
                        "DependsOnAnotherModulesDomain",
                        "DependsOnAnotherModulesService",
                        "AnnotatedAsASpringComponent",
                        "package-info");
    }

    @Test
    void GivenAUseCaseUsingEverythingAllowedAndTransactional_WhenRule1Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(
                        ApplicationRules.onlyAllowedDependencies(ROOT, FIXTURE_MODULE_ANNOTATIONS),
                        FixtureClasses.only(UpperUseCase.class)))
                .isEmpty();
    }

    @Test
    void GivenAModuleAnnotationOnAnApplicationPackageInfo_WhenRule1Evaluates_ThenNothingIsFlagged() {
        JavaClasses api = FixtureClasses.inPackage(ZETA + ".upper.application.api");

        assertThat(api.stream().map(JavaClass::getSimpleName)).contains("package-info");
        assertThat(violators(ApplicationRules.onlyAllowedDependencies(ROOT, FIXTURE_MODULE_ANNOTATIONS), api))
                .isEmpty();
    }

    @Test
    void GivenAnotherFrameworksAnnotationOnAnApplicationPackageInfo_WhenRule1Evaluates_ThenItIsFlagged() {
        JavaClasses configured = FixtureClasses.inPackage(ZETA + ".upper.application.configured");

        assertThat(violators(ApplicationRules.onlyAllowedDependencies(ROOT, FIXTURE_MODULE_ANNOTATIONS), configured))
                .containsExactly("package-info");
    }

    @Test
    void GivenTheZetaFixtures_WhenRule2Evaluates_ThenExactlyTheOffGraphClassesAreFlagged() {
        assertThat(violators(ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH), zeta()))
                .containsExactlyInAnyOrder("LowerReachesUp", "LowerAdapterReachesUp", "StrayValue");
    }

    @Test
    void GivenADependencyAgainstAnArrow_WhenRule2Evaluates_ThenItIsFlaggedInTheApplicationAndInAnAdapter() {
        assertThat(violators(
                        ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH),
                        FixtureClasses.only(LowerReachesUp.class, LowerAdapterReachesUp.class)))
                .containsExactlyInAnyOrder("LowerReachesUp", "LowerAdapterReachesUp");
    }

    @Test
    void GivenAModuleMissingFromTheGraph_WhenRule2Evaluates_ThenItsClassIsFlagged() {
        assertThat(violators(
                        ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH),
                        FixtureClasses.only(StrayValue.class)))
                .containsExactly("StrayValue");
    }

    @Test
    void GivenAGraphWithTheArrowAdded_WhenRule2Evaluates_ThenTheSameDependencyIsAllowed() {
        Map<String, Set<String>> bothWays = Map.of("lower", Set.of("upper"), "upper", Set.of("lower"));

        assertThat(violators(
                        ApplicationRules.modulesDependAlongTheGraph(ROOT, bothWays),
                        FixtureClasses.only(LowerReachesUp.class, LowerAdapterReachesUp.class)))
                .isEmpty();
    }

    @Test
    void GivenTheRootTheWebAndAnArrowOnTheGraph_WhenRule2Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(
                        ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH),
                        FixtureClasses.only(
                                ZetaApplication.class, ZetaWebHandler.class, UpperUseCase.class, LowerHappened.class)))
                .isEmpty();
    }

    @Test
    void GivenTheZetaFixtures_WhenRule3Evaluates_ThenTheApiAndTheSpiRecordsHoldingADomainTypeAreFlagged() {
        assertThat(violators(ApplicationRules.apiAndSpiUseNoDomainType(ROOT), zeta()))
                .containsExactlyInAnyOrder("LowerFactLeaksTheDomain", "UsageFactLeaksTheDomain");
    }

    @Test
    void GivenTheZetaFixtures_WhenRule4EvaluatesTheApis_ThenEveryApiWithoutOneImplementationAtHomeIsFlagged() {
        assertThat(violators(ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT), zeta()))
                .containsExactlyInAnyOrder("Unimplemented", "ImplementedTwice", "ImplementedElsewhere");
    }

    @Test
    void GivenAnApiWithNoImplementation_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(Unimplemented.class)))
                .containsExactly("Unimplemented");
    }

    @Test
    void GivenAnApiWithTwoImplementations_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(ImplementedTwice.class)))
                .containsExactly("ImplementedTwice");
    }

    @Test
    void GivenAnApiImplementedInAnotherModule_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(ImplementedElsewhere.class)))
                .containsExactly("ImplementedElsewhere");
    }

    @Test
    void GivenAnApiWithOneImplementationBesideAnAbstractBaseAndASealedFact_WhenRule4Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(LowerFacts.class, LowerOutcome.class)))
                .isEmpty();
    }

    @Test
    void GivenTheZetaFixtures_WhenRule4EvaluatesTheSpis_ThenEverySpiWithoutOneImplementationElsewhereIsFlagged() {
        assertThat(violators(ApplicationRules.eachSpiHasOneImplementationOutsideItsModule(ROOT), zeta()))
                .containsExactlyInAnyOrder("UsageImplementedAtHome", "UsageUnimplemented");
    }

    @Test
    void GivenAnSpiImplementedInItsOwnModule_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachSpiHasOneImplementationOutsideItsModule(ROOT),
                        FixtureClasses.only(UsageImplementedAtHome.class)))
                .containsExactly("UsageImplementedAtHome");
    }

    @Test
    void GivenAnSpiWithNoImplementation_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachSpiHasOneImplementationOutsideItsModule(ROOT),
                        FixtureClasses.only(UsageUnimplemented.class)))
                .containsExactly("UsageUnimplemented");
    }

    @Test
    void GivenAnSpiImplementedOnceInAnotherModule_WhenRule4Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachSpiHasOneImplementationOutsideItsModule(ROOT),
                        FixtureClasses.only(LowerUsage.class)))
                .isEmpty();
    }

    @Test
    void GivenTheZetaFixtures_WhenTheNullMarkedGuardChecks_ThenTheMissingAndTheUnmarkedPackageInfoAreReported() {
        assertThat(ApplicationRules.packagesNotNullMarked(ROOT, zeta()))
                .containsExactly(ROOT + ".upper.application.api", ROOT + ".upper.application.port.out");
    }

    @Test
    void GivenTheZetaFixtures_WhenNullMarkedSubjectsAreListed_ThenEveryApplicationPackageAndNoOtherIsAmongThem() {
        assertThat(ApplicationRules.nullMarkedSubjects(ROOT, zeta()))
                .containsExactly(
                        ROOT + ".lower.application.api",
                        ROOT + ".lower.application.service",
                        ROOT + ".lower.application.spi",
                        ROOT + ".shared.application",
                        ROOT + ".upper.application.api",
                        ROOT + ".upper.application.configured",
                        ROOT + ".upper.application.port.out",
                        ROOT + ".upper.application.service");
    }

    @Test
    void GivenTheZetaFixtures_WhenRule6Evaluates_ThenTheKernelRootTheDomainAndTheEventThatUseAPortAreFlagged() {
        assertThat(violators(ApplicationRules.domainReachesNoApplicationPort(ROOT), zeta()))
                .containsExactlyInAnyOrder(
                        "KernelRootUsesTheClock", "LowerDomainUsesTheClock", "LowerEventUsesTheClock");
    }
}
