package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.archfixture.modulith.NamedInterface;
import com.dispatchflow.archfixture.zeta.ZetaApplication;
import com.dispatchflow.archfixture.zeta.lower.adapter.out.ImplementedOutsideTheServiceService;
import com.dispatchflow.archfixture.zeta.lower.adapter.out.LowerAdapterReachesUp;
import com.dispatchflow.archfixture.zeta.lower.application.api.ImplementedElsewhere;
import com.dispatchflow.archfixture.zeta.lower.application.api.ImplementedOutsideTheService;
import com.dispatchflow.archfixture.zeta.lower.application.api.ImplementedTwice;
import com.dispatchflow.archfixture.zeta.lower.application.api.ImplementedUnderAnotherName;
import com.dispatchflow.archfixture.zeta.lower.application.api.LowerFactLeaksTheDomain;
import com.dispatchflow.archfixture.zeta.lower.application.api.LowerFacts;
import com.dispatchflow.archfixture.zeta.lower.application.api.LowerFactsLeakThroughAList;
import com.dispatchflow.archfixture.zeta.lower.application.api.LowerOutcome;
import com.dispatchflow.archfixture.zeta.lower.application.api.LowerVerdict;
import com.dispatchflow.archfixture.zeta.lower.application.api.SealedOverAClass;
import com.dispatchflow.archfixture.zeta.lower.application.api.Unimplemented;
import com.dispatchflow.archfixture.zeta.lower.application.service.ImplementedUnderAnotherNameHandler;
import com.dispatchflow.archfixture.zeta.lower.application.service.LowerReachesUp;
import com.dispatchflow.archfixture.zeta.lower.application.service.UsageImplementedAtHomeService;
import com.dispatchflow.archfixture.zeta.lower.application.spi.LowerUsage;
import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageFactLeaksTheDomain;
import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageImplementedAtHome;
import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageImplementedByTheWrongModule;
import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageMissingFromTheMap;
import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageUnimplemented;
import com.dispatchflow.archfixture.zeta.lower.domain.LowerDomainUsesTheClock;
import com.dispatchflow.archfixture.zeta.lower.events.LowerEventUsesTheClock;
import com.dispatchflow.archfixture.zeta.lower.events.LowerHappened;
import com.dispatchflow.archfixture.zeta.peer.application.service.CallsAnSpiInterfaceNamedForAnotherModule;
import com.dispatchflow.archfixture.zeta.peer.application.service.PeerUsageService;
import com.dispatchflow.archfixture.zeta.report.adapter.out.ReportAdapterReadsTheDomain;
import com.dispatchflow.archfixture.zeta.report.application.service.ReportHearsTheEvent;
import com.dispatchflow.archfixture.zeta.report.application.service.ReportReadsAnAccount;
import com.dispatchflow.archfixture.zeta.report.application.service.ReportReadsTheApi;
import com.dispatchflow.archfixture.zeta.report.application.service.ReportTakesTheActor;
import com.dispatchflow.archfixture.zeta.shared.KernelRootUsesTheClock;
import com.dispatchflow.archfixture.zeta.shared.application.SharedPortUsesSpring;
import com.dispatchflow.archfixture.zeta.side.application.service.CallsAnSpiItDoesNotImplement;
import com.dispatchflow.archfixture.zeta.side.application.service.UsageImplementedByTheWrongModuleService;
import com.dispatchflow.archfixture.zeta.stray.StrayValue;
import com.dispatchflow.archfixture.zeta.upper.application.port.out.PortRootUsingASubpackage;
import com.dispatchflow.archfixture.zeta.upper.application.port.out.UpperRecords;
import com.dispatchflow.archfixture.zeta.upper.application.port.out.looped.LoopedRecords;
import com.dispatchflow.archfixture.zeta.upper.application.port.out.used.UsedByThePortRoot;
import com.dispatchflow.archfixture.zeta.upper.application.service.AnnotatedAsANamedInterface;
import com.dispatchflow.archfixture.zeta.upper.application.service.AnnotatedAsASpringComponent;
import com.dispatchflow.archfixture.zeta.upper.application.service.DependsOnADomainLibrary;
import com.dispatchflow.archfixture.zeta.upper.application.service.DependsOnAnotherModulesDomain;
import com.dispatchflow.archfixture.zeta.upper.application.service.DependsOnAnotherModulesService;
import com.dispatchflow.archfixture.zeta.upper.application.service.DependsOnItsOwnAdapter;
import com.dispatchflow.archfixture.zeta.upper.application.service.LowerUsageService;
import com.dispatchflow.archfixture.zeta.upper.application.service.ServiceRootUsingASubpackage;
import com.dispatchflow.archfixture.zeta.upper.application.service.UpperUseCase;
import com.dispatchflow.archfixture.zeta.upper.application.service.looped.LoopedUseCase;
import com.dispatchflow.archfixture.zeta.upper.application.service.nested.NestedPackageUsingASubpackage;
import com.dispatchflow.archfixture.zeta.upper.application.service.used.UsedByTheServiceRoot;
import com.dispatchflow.archfixture.zeta.web.ZetaWebHandler;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ViolationHandler;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Proves each application rule fires on the fixtures under {@code archfixture.zeta}, which is the
 * rules' root here: its {@code lower}, {@code upper}, {@code side}, {@code peer}, {@code report}
 * and {@code gate} are modules, {@code report} reading {@code lower} as Reporting reads its sources
 * and taking only {@code gate}'s actor as Reporting takes Access's, {@code stray} is a top-level
 * package missing from the fixture graph, and {@code shared} and {@code web} are no modules. Every
 * test names the exact classes a rule flags, by their fully qualified names, so a rule that flags
 * too much fails as surely as one that flags nothing, and two fixtures of one simple name can't
 * stand in for each other.
 */
class ApplicationRulesTest {

    private static final String ZETA = "zeta";
    private static final String ROOT = FixtureClasses.ROOT + "." + ZETA;
    private static final String FIXTURE_MODULE_ANNOTATIONS = NamedInterface.class.getPackageName();
    private static final String PACKAGE_INFO = ".package-info";
    private static final String LOOPED_USE_CASES = ROOT + ".upper.application.service.looped";
    private static final String LOOPED_PORTS = ROOT + ".upper.application.port.out.looped";
    // One line of a cycle's header: "Cycle detected: Slice a -> ", then "  Slice b -> ", then
    // "  Slice a".
    private static final Pattern CYCLE_STEP = Pattern.compile("^(?:Cycle detected: |\\s*)Slice (\\S+)(?: -> )?\\s*$");
    // Two slices and the first again, which closes the loop.
    private static final int CLOSED_LOOP_OF_TWO = 3;
    // upper, side, peer and report may depend on lower, and report on gate too; lower and gate on
    // nothing. stray is left out on purpose.
    private static final Map<String, Set<String>> GRAPH = Map.of(
            "lower", Set.of(),
            "gate", Set.of(),
            "upper", Set.of("lower"),
            "side", Set.of("lower"),
            "peer", Set.of("lower"),
            "report", Set.of("lower", "gate"));
    // Every arrow but PeerUsage's points at upper. UsageImplementedByTheWrongModule is implemented
    // in side instead, and UsageMissingFromTheMap is left out on purpose.
    private static final Map<String, String> SPI_IMPLEMENTERS = Map.of(
            "lower.application.spi.LowerUsage", "upper",
            "lower.application.spi.UsageImplementedAtHome", "upper",
            "lower.application.spi.UsageUnimplemented", "upper",
            "lower.application.spi.UsageImplementedByTheWrongModule", "upper",
            "lower.application.spi.PeerUsage", "peer");
    // Every fixture spi is implemented by its XService, so no port is renamed.
    private static final Map<String, String> NO_RENAMED_PORTS = Map.of();
    private static final String READER = "report";
    private static final Set<String> READERS_SOURCES = Set.of("lower");
    // Of gate's api the reader may take the actor alone, as Reporting takes only Access's Actor.
    private static final Map<String, Set<String>> READERS_NARROWED_APIS =
            Map.of("gate", Set.of("gate.application.api.GateActor"));

    private static ArchRule rule1() {
        return ApplicationRules.onlyAllowedDependencies(ROOT, FIXTURE_MODULE_ANNOTATIONS, SPI_IMPLEMENTERS);
    }

    private static ArchRule spiRule4(Map<String, String> implementers) {
        return ApplicationRules.eachSpiHasOneImplementationInItsArrowsModule(ROOT, implementers, NO_RENAMED_PORTS);
    }

    private static ArchRule rule8() {
        return ApplicationRules.readsOnlyTheEventsOf(ROOT, READER, READERS_SOURCES, READERS_NARROWED_APIS);
    }

    private static JavaClasses zeta() {
        return FixtureClasses.inPackage(ZETA);
    }

    /** The fully qualified names of the classes a rule reports a violation against. */
    private static Set<String> violators(ArchRule rule, JavaClasses classes) {
        Set<String> names = new TreeSet<>();
        ViolationHandler<JavaClass> collect =
                (violating, message) -> violating.forEach(type -> names.add(type.getName()));
        rule.evaluate(classes).handleViolations(collect);
        return names;
    }

    /** Rule 7's cycle report: one detail per cycle found. */
    private static List<String> cycleDetails(JavaClasses classes) {
        return ApplicationRules.applicationPackagesAreFreeOfCycles(ROOT)
                .evaluate(classes)
                .getFailureReport()
                .getDetails();
    }

    /**
     * The cycle's header walks the two looped slices and closes on the first. Which of the two
     * comes first is ArchUnit's choice, so it is left open.
     */
    private static void isTheLoopedCycle(String detail) {
        List<String> walked = detail.lines()
                .map(CYCLE_STEP::matcher)
                .takeWhile(Matcher::matches)
                .map(step -> step.group(1))
                .toList();

        assertThat(walked).as("the slices the cycle walks in %s", detail).hasSize(CLOSED_LOOP_OF_TWO);
        assertThat(walked.subList(0, CLOSED_LOOP_OF_TWO - 1))
                .as("the slices the cycle walks in %s", detail)
                .containsExactlyInAnyOrder(LOOPED_USE_CASES, LOOPED_PORTS);
        assertThat(walked.getLast()).as("the slice that closes the cycle").isEqualTo(walked.getFirst());
    }

    private static String[] namesOf(Class<?>... types) {
        return Arrays.stream(types).map(Class::getName).toArray(String[]::new);
    }

    @Test
    void GivenTheZetaFixtures_WhenRule1Evaluates_ThenExactlyTheForbiddenDependenciesAreFlagged() {
        assertThat(violators(rule1(), zeta()))
                .containsExactlyInAnyOrder(
                        SharedPortUsesSpring.class.getName(),
                        DependsOnAnotherModulesDomain.class.getName(),
                        DependsOnAnotherModulesService.class.getName(),
                        DependsOnItsOwnAdapter.class.getName(),
                        DependsOnADomainLibrary.class.getName(),
                        AnnotatedAsASpringComponent.class.getName(),
                        AnnotatedAsANamedInterface.class.getName(),
                        CallsAnSpiItDoesNotImplement.class.getName(),
                        CallsAnSpiInterfaceNamedForAnotherModule.class.getName(),
                        // No arrow names side for lower's spi, so rule 1 refuses its implementation
                        // too.
                        UsageImplementedByTheWrongModuleService.class.getName(),
                        ROOT + ".upper.application.configured" + PACKAGE_INFO);
    }

    @Test
    void GivenAUseCaseHoldingAJtsPoint_WhenRule1Evaluates_ThenItIsFlagged() {
        assertThat(violators(rule1(), FixtureClasses.only(DependsOnADomainLibrary.class)))
                .containsExactly(DependsOnADomainLibrary.class.getName());
    }

    @Test
    void GivenAModuleThatNeitherOwnsNorImplementsAnotherModulesSpi_WhenRule1Evaluates_ThenItsUseOfTheSpiIsFlagged() {
        assertThat(violators(rule1(), FixtureClasses.only(CallsAnSpiItDoesNotImplement.class)))
                .containsExactly(CallsAnSpiItDoesNotImplement.class.getName());
    }

    @Test
    void GivenAModuleThatImplementsAnotherOfTheOwnersSpiInterfaces_WhenRule1Evaluates_ThenItsUseOfThisOneIsFlagged() {
        assertThat(violators(rule1(), FixtureClasses.only(CallsAnSpiInterfaceNamedForAnotherModule.class)))
                .containsExactly(CallsAnSpiInterfaceNamedForAnotherModule.class.getName());
    }

    @Test
    void GivenAnImplementerUsingItsSpiInterfaceAndTheRecordBesideIt_WhenRule1Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(rule1(), FixtureClasses.only(PeerUsageService.class)))
                .isEmpty();
    }

    @Test
    void GivenAMapNamingTheCallerAsAnImplementerOfThatSpi_WhenRule1Evaluates_ThenItsUseOfTheSpiIsAllowed() {
        Map<String, String> sideImplements = Map.of("lower.application.spi.LowerUsage", "side");

        assertThat(violators(
                        ApplicationRules.onlyAllowedDependencies(ROOT, FIXTURE_MODULE_ANNOTATIONS, sideImplements),
                        FixtureClasses.only(CallsAnSpiItDoesNotImplement.class)))
                .isEmpty();
    }

    @Test
    void GivenTheSpisOwnerAndItsNamedImplementer_WhenRule1Evaluates_ThenTheirUsesOfTheSpiAreAllowed() {
        assertThat(violators(
                        rule1(),
                        FixtureClasses.only(
                                UsageImplementedAtHomeService.class, LowerUsageService.class, UpperUseCase.class)))
                .isEmpty();
    }

    @Test
    void GivenAUseCaseUsingEverythingAllowedAndTransactional_WhenRule1Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(rule1(), FixtureClasses.only(UpperUseCase.class))).isEmpty();
    }

    @Test
    void GivenAUseCaseThatDependsOnItsOwnModulesAdapter_WhenRule1Evaluates_ThenItIsFlagged() {
        assertThat(violators(rule1(), FixtureClasses.only(DependsOnItsOwnAdapter.class)))
                .containsExactly(DependsOnItsOwnAdapter.class.getName());
    }

    @Test
    void GivenAModuleAnnotationOnAnApplicationClass_WhenRule1Evaluates_ThenItIsFlagged() {
        assertThat(violators(rule1(), FixtureClasses.only(AnnotatedAsANamedInterface.class)))
                .containsExactly(AnnotatedAsANamedInterface.class.getName());
    }

    @Test
    void GivenAModuleAnnotationOnAnApplicationPackageInfo_WhenRule1Evaluates_ThenNothingIsFlagged() {
        JavaClasses api = FixtureClasses.inPackage(ZETA + ".upper.application.api");

        assertThat(api.stream().map(JavaClass::getSimpleName)).contains("package-info");
        assertThat(violators(rule1(), api)).isEmpty();
    }

    @Test
    void GivenAnotherFrameworksAnnotationOnAnApplicationPackageInfo_WhenRule1Evaluates_ThenItIsFlagged() {
        JavaClasses configured = FixtureClasses.inPackage(ZETA + ".upper.application.configured");

        assertThat(violators(rule1(), configured))
                .containsExactly(ROOT + ".upper.application.configured" + PACKAGE_INFO);
    }

    @Test
    void GivenTheZetaFixtures_WhenRule2Evaluates_ThenExactlyTheOffGraphClassesAreFlagged() {
        assertThat(violators(ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH), zeta()))
                .containsExactlyInAnyOrder(
                        namesOf(LowerReachesUp.class, LowerAdapterReachesUp.class, StrayValue.class));
    }

    @Test
    void GivenADependencyAgainstAnArrow_WhenRule2Evaluates_ThenItIsFlaggedInTheApplicationAndInAnAdapter() {
        assertThat(violators(
                        ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH),
                        FixtureClasses.only(LowerReachesUp.class, LowerAdapterReachesUp.class)))
                .containsExactlyInAnyOrder(namesOf(LowerReachesUp.class, LowerAdapterReachesUp.class));
    }

    @Test
    void GivenAModuleMissingFromTheGraph_WhenRule2Evaluates_ThenItsClassIsFlagged() {
        assertThat(violators(
                        ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH),
                        FixtureClasses.only(StrayValue.class)))
                .containsExactly(StrayValue.class.getName());
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
                .containsExactlyInAnyOrder(namesOf(
                        LowerFactLeaksTheDomain.class,
                        LowerFactsLeakThroughAList.class,
                        UsageFactLeaksTheDomain.class));
    }

    @Test
    void GivenAnApiRecordHoldingAListOfADomainType_WhenRule3Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.apiAndSpiUseNoDomainType(ROOT),
                        FixtureClasses.only(LowerFactsLeakThroughAList.class)))
                .containsExactly(LowerFactsLeakThroughAList.class.getName());
    }

    @Test
    void GivenTheZetaFixtures_WhenRule4EvaluatesTheApis_ThenEveryApiWithoutOneImplementationAtHomeIsFlagged() {
        assertThat(violators(ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT), zeta()))
                .containsExactlyInAnyOrder(namesOf(
                        Unimplemented.class,
                        ImplementedTwice.class,
                        ImplementedElsewhere.class,
                        ImplementedOutsideTheService.class,
                        ImplementedUnderAnotherName.class,
                        SealedOverAClass.class));
    }

    @Test
    void GivenAnApiWithNoImplementation_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(Unimplemented.class)))
                .containsExactly(Unimplemented.class.getName());
    }

    @Test
    void GivenAnApiWithTwoImplementations_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(ImplementedTwice.class)))
                .containsExactly(ImplementedTwice.class.getName());
    }

    @Test
    void GivenAnApiImplementedInAnotherModule_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(ImplementedElsewhere.class)))
                .containsExactly(ImplementedElsewhere.class.getName());
    }

    @Test
    void GivenAnApiImplementedInItsModuleOutsideItsService_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(
                                ImplementedOutsideTheService.class, ImplementedOutsideTheServiceService.class)))
                .containsExactly(ImplementedOutsideTheService.class.getName());
    }

    @Test
    void GivenAnApiImplementedInItsServiceUnderAnotherNameThanXService_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(
                                ImplementedUnderAnotherName.class, ImplementedUnderAnotherNameHandler.class)))
                .containsExactly(ImplementedUnderAnotherName.class.getName());
    }

    @Test
    void GivenASealedApiInterfaceThatPermitsAPlainClass_WhenRule4Evaluates_ThenItIsCheckedAndFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(SealedOverAClass.class)))
                .containsExactly(SealedOverAClass.class.getName());
    }

    @Test
    void GivenAnApiWithOneImplementationBesideAnAbstractBaseAndASealedFact_WhenRule4Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(
                        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT),
                        FixtureClasses.only(LowerFacts.class, LowerOutcome.class, LowerVerdict.class)))
                .isEmpty();
    }

    @Test
    void GivenTheZetaFixtures_WhenRule4EvaluatesTheSpis_ThenEverySpiNotImplementedOnceWhereItsArrowPointsIsFlagged() {
        assertThat(violators(spiRule4(SPI_IMPLEMENTERS), zeta()))
                .containsExactlyInAnyOrder(namesOf(
                        UsageImplementedAtHome.class,
                        UsageUnimplemented.class,
                        UsageImplementedByTheWrongModule.class,
                        UsageMissingFromTheMap.class));
    }

    @Test
    void GivenAnSpiImplementedInItsOwnModule_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(spiRule4(SPI_IMPLEMENTERS), FixtureClasses.only(UsageImplementedAtHome.class)))
                .containsExactly(UsageImplementedAtHome.class.getName());
    }

    @Test
    void GivenAnSpiImplementedByAnotherModuleThanItsArrowNames_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(spiRule4(SPI_IMPLEMENTERS), FixtureClasses.only(UsageImplementedByTheWrongModule.class)))
                .containsExactly(UsageImplementedByTheWrongModule.class.getName());
    }

    @Test
    void GivenAnSpiMissingFromTheMap_WhenRule4Evaluates_ThenItIsFlaggedThoughItHasOneOutsideImplementation() {
        assertThat(violators(spiRule4(SPI_IMPLEMENTERS), FixtureClasses.only(UsageMissingFromTheMap.class)))
                .containsExactly(UsageMissingFromTheMap.class.getName());
    }

    @Test
    void GivenAnSpiWithNoImplementation_WhenRule4Evaluates_ThenItIsFlagged() {
        assertThat(violators(spiRule4(SPI_IMPLEMENTERS), FixtureClasses.only(UsageUnimplemented.class)))
                .containsExactly(UsageUnimplemented.class.getName());
    }

    @Test
    void GivenAnSpiImplementedOnceWhereItsArrowPoints_WhenRule4Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(spiRule4(SPI_IMPLEMENTERS), FixtureClasses.only(LowerUsage.class)))
                .isEmpty();
    }

    @Test
    void GivenANameMapThatRenamesAnSpisImplementation_WhenRule4Evaluates_ThenItsXServiceIsFlagged() {
        Map<String, String> renamed = Map.of("lower.application.spi.LowerUsage", "UpperUsageService");

        assertThat(violators(
                        ApplicationRules.eachSpiHasOneImplementationInItsArrowsModule(ROOT, SPI_IMPLEMENTERS, renamed),
                        FixtureClasses.only(LowerUsage.class)))
                .containsExactly(LowerUsage.class.getName());
    }

    @Test
    void GivenAMapWithTheWrongModulesArrow_WhenRule4Evaluates_ThenTheSameImplementationIsAllowed() {
        Map<String, String> pointingAtSide = Map.of("lower.application.spi.UsageImplementedByTheWrongModule", "side");

        assertThat(violators(spiRule4(pointingAtSide), FixtureClasses.only(UsageImplementedByTheWrongModule.class)))
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
                        ROOT + ".gate.application.api",
                        ROOT + ".lower.application.api",
                        ROOT + ".lower.application.service",
                        ROOT + ".lower.application.spi",
                        ROOT + ".peer.application.service",
                        ROOT + ".report.application.service",
                        ROOT + ".shared.application",
                        ROOT + ".side.application.service",
                        ROOT + ".upper.application.api",
                        ROOT + ".upper.application.configured",
                        ROOT + ".upper.application.port.out",
                        ROOT + ".upper.application.port.out.looped",
                        ROOT + ".upper.application.port.out.used",
                        ROOT + ".upper.application.service",
                        ROOT + ".upper.application.service.looped",
                        ROOT + ".upper.application.service.nested",
                        ROOT + ".upper.application.service.nested.below",
                        ROOT + ".upper.application.service.used");
    }

    @Test
    void GivenTheZetaFixtures_WhenRule6Evaluates_ThenTheKernelRootTheDomainAndTheEventThatUseAPortAreFlagged() {
        assertThat(violators(ApplicationRules.domainReachesNoApplicationPort(ROOT), zeta()))
                .containsExactlyInAnyOrder(namesOf(
                        KernelRootUsesTheClock.class, LowerDomainUsesTheClock.class, LowerEventUsesTheClock.class));
    }

    @Test
    void GivenAUseCasePackageAndAPortPackageThatUseEachOther_WhenRule7ChecksCycles_ThenItIsFlagged() {
        assertThat(cycleDetails(FixtureClasses.only(LoopedUseCase.class, LoopedRecords.class)))
                .singleElement()
                .satisfies(ApplicationRulesTest::isTheLoopedCycle);
    }

    @Test
    void GivenAGraphWithALoop_WhenItsCyclesAreSought_ThenExactlyTheModulesOnTheLoopAreFound() {
        Map<String, Set<String>> looped = Map.of(
                "first", Set.of("second"),
                "second", Set.of("third"),
                "third", Set.of("first"),
                "above", Set.of("first"),
                "self", Set.of("self"),
                "below", Set.of(),
                "dangling", Set.of("absent"));

        assertThat(ApplicationRules.modulesOnACycle(looped)).containsExactly("first", "second", "self", "third");
    }

    @Test
    void GivenTheFixtureGraph_WhenItsCyclesAreSought_ThenNoneIsFound() {
        assertThat(ApplicationRules.modulesOnACycle(GRAPH)).isEmpty();
    }

    @Test
    void GivenTheZetaFixtures_WhenRule7ChecksCycles_ThenTheLoopIsTheOneCycleFlagged() {
        assertThat(cycleDetails(zeta())).singleElement().satisfies(ApplicationRulesTest::isTheLoopedCycle);
    }

    @Test
    void GivenUseCasesThatUseTheirPortsAndSubpackagesOneWay_WhenRule7ChecksCycles_ThenNothingIsFlagged() {
        assertThat(ApplicationRules.applicationPackagesAreFreeOfCycles(ROOT)
                        .evaluate(FixtureClasses.only(
                                UpperUseCase.class,
                                UpperRecords.class,
                                ServiceRootUsingASubpackage.class,
                                UsedByTheServiceRoot.class,
                                PortRootUsingASubpackage.class,
                                UsedByThePortRoot.class))
                        .hasViolation())
                .isFalse();
    }

    @Test
    void GivenTheZetaFixtures_WhenRule7ChecksTheRoots_ThenExactlyThePackagesUsingTheirOwnSubpackageAreFlagged() {
        assertThat(violators(ApplicationRules.applicationPackagesUseNoSubpackage(ROOT), zeta()))
                .containsExactlyInAnyOrder(namesOf(
                        ServiceRootUsingASubpackage.class,
                        PortRootUsingASubpackage.class,
                        NestedPackageUsingASubpackage.class));
    }

    @Test
    void GivenRootsThatUseOnlyTheirOwnPackageAndOtherLayers_WhenRule7ChecksTheRoots_ThenNothingIsFlagged() {
        assertThat(violators(
                        ApplicationRules.applicationPackagesUseNoSubpackage(ROOT),
                        FixtureClasses.only(UpperUseCase.class, UpperRecords.class, LoopedUseCase.class)))
                .isEmpty();
    }

    @Test
    void GivenTheZetaFixtures_WhenRule8Evaluates_ThenExactlyTheReadersClassesUsingMoreThanTheSourcesEventsAreFlagged() {
        assertThat(violators(rule8(), zeta()))
                .containsExactlyInAnyOrder(namesOf(
                        ReportReadsTheApi.class, ReportAdapterReadsTheDomain.class, ReportReadsAnAccount.class));
    }

    @Test
    void GivenAReaderUsingASourcesApi_WhenRule8Evaluates_ThenItIsFlaggedThoughRules1And2AllowIt() {
        JavaClasses reader = FixtureClasses.only(ReportReadsTheApi.class);

        assertThat(violators(rule1(), reader)).isEmpty();
        assertThat(violators(ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH), reader))
                .isEmpty();
        assertThat(violators(rule8(), reader)).containsExactly(ReportReadsTheApi.class.getName());
    }

    @Test
    void GivenAReadersAdapterUsingASourcesDomain_WhenRule8Evaluates_ThenItIsFlagged() {
        assertThat(violators(rule8(), FixtureClasses.only(ReportAdapterReadsTheDomain.class)))
                .containsExactly(ReportAdapterReadsTheDomain.class.getName());
    }

    @Test
    void GivenAReaderUsingAnotherApiTypeOfTheActorsModule_WhenRule8Evaluates_ThenItIsFlaggedThoughRules1And2AllowIt() {
        JavaClasses reader = FixtureClasses.only(ReportReadsAnAccount.class);

        assertThat(violators(rule1(), reader)).isEmpty();
        assertThat(violators(ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH), reader))
                .isEmpty();
        assertThat(violators(rule8(), reader)).containsExactly(ReportReadsAnAccount.class.getName());
    }

    @Test
    void GivenAReaderTakingTheActor_WhenRule8Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(rule8(), FixtureClasses.only(ReportTakesTheActor.class)))
                .isEmpty();
    }

    @Test
    void GivenAReaderHearingASourcesEvent_WhenRule8Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(rule8(), FixtureClasses.only(ReportHearsTheEvent.class)))
                .isEmpty();
    }
}
