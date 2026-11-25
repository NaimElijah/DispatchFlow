package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.archfixture.modulith.NamedInterface;
import com.dispatchflow.archfixture.zeta.ImportsPeersUseCase;
import com.dispatchflow.archfixture.zeta.ZetaApplication;
import com.dispatchflow.archfixture.zeta.lower.LowerRootValue;
import com.dispatchflow.archfixture.zeta.lower.adapter.in.web.LowerForm;
import com.dispatchflow.archfixture.zeta.lower.adapter.out.ImplementedOutsideTheServiceService;
import com.dispatchflow.archfixture.zeta.lower.adapter.out.LowerAdapterReachesUp;
import com.dispatchflow.archfixture.zeta.lower.adapter.out.cache.deep.LowerEntry;
import com.dispatchflow.archfixture.zeta.lower.adapter.out.optimizer.LowerOptimizer;
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
import com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.ClockHearsAModulesEvent;
import com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.ClockReadsAModulesDomain;
import com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.ClockReadsAModulesPort;
import com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.ClockReadsThePersistence;
import com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.ClockUsesTheWebStreams;
import com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.SharedClockReadsAModulesStore;
import com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.ZetaClock;
import com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.system.SystemClockReadsAModule;
import com.dispatchflow.archfixture.zeta.shared.adapter.out.optimizer.ZetaSolverClient;
import com.dispatchflow.archfixture.zeta.shared.application.SharedPortUsesSpring;
import com.dispatchflow.archfixture.zeta.side.application.service.CallsAnSpiItDoesNotImplement;
import com.dispatchflow.archfixture.zeta.side.application.service.UsageImplementedByTheWrongModuleService;
import com.dispatchflow.archfixture.zeta.stray.StrayValue;
import com.dispatchflow.archfixture.zeta.upper.UpperConfiguration;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.listen.ListenerReadsTheCurrentCaller;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.listen.ListenerUsesTheWebAdapter;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.ControllerReachesTheWeb;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.ControllerReadsTheCurrentCaller;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.ControllerTakesAReader;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.ControllerTakesAStream;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.ControllerTakesAStreamedResource;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.ControllerTakesAWriter;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.ControllerTakesAnOutputStream;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.ControllerTakesItsArguments;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.ControllerTakesTheRequest;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.HandlerWritesTheResponse;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.InboundUsesTheStore;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.PlainClassTakesAStream;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.WebCallsTheSolverClient;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.WebUsesTheLowerCache;
import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.form.FormUsesTheRows;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.UpperOutboxUsesTheMail;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.mail.MailReadsTheClock;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.mail.MailUsesTheStore;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.mirror.MirrorsTheLowerCache;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.mirror.MirrorsTheLowerForm;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.mirror.deep.MirrorsADeepEntry;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.optimizer.UpperOptimizer;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.push.PushReadsTheRequest;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.relay.RelayUsesLowersCommon;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.relay.RelayUsesTheCommon;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.store.StoreSavesThroughTheKernel;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.store.UpperTable;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.store.rows.RowReadsTheRequest;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.store.rows.UpperRow;
import com.dispatchflow.archfixture.zeta.upper.application.api.UpperCurrentCaller;
import com.dispatchflow.archfixture.zeta.upper.application.port.out.PortRootUsingASubpackage;
import com.dispatchflow.archfixture.zeta.upper.application.port.out.UpperRecords;
import com.dispatchflow.archfixture.zeta.upper.application.port.out.looped.LoopedRecords;
import com.dispatchflow.archfixture.zeta.upper.application.port.out.nested.NestedPortUsingASubpackage;
import com.dispatchflow.archfixture.zeta.upper.application.port.out.used.UsedByThePortRoot;
import com.dispatchflow.archfixture.zeta.upper.application.service.AnnotatedAsANamedInterface;
import com.dispatchflow.archfixture.zeta.upper.application.service.AnnotatedAsASpringComponent;
import com.dispatchflow.archfixture.zeta.upper.application.service.DependsOnADomainLibrary;
import com.dispatchflow.archfixture.zeta.upper.application.service.DependsOnAnotherModulesDomain;
import com.dispatchflow.archfixture.zeta.upper.application.service.DependsOnAnotherModulesService;
import com.dispatchflow.archfixture.zeta.upper.application.service.DependsOnItsOwnAdapter;
import com.dispatchflow.archfixture.zeta.upper.application.service.DependsOnTheKernelsAdapter;
import com.dispatchflow.archfixture.zeta.upper.application.service.LowerUsageService;
import com.dispatchflow.archfixture.zeta.upper.application.service.ServiceReadsTheCurrentCaller;
import com.dispatchflow.archfixture.zeta.upper.application.service.ServiceRootUsingASubpackage;
import com.dispatchflow.archfixture.zeta.upper.application.service.UpperUseCase;
import com.dispatchflow.archfixture.zeta.upper.application.service.looped.LoopedUseCase;
import com.dispatchflow.archfixture.zeta.upper.application.service.nested.NestedPackageUsingASubpackage;
import com.dispatchflow.archfixture.zeta.upper.application.service.prefix.UsesASiblingWithALongerName;
import com.dispatchflow.archfixture.zeta.upper.application.service.prefixed.SiblingWithALongerName;
import com.dispatchflow.archfixture.zeta.upper.application.service.upward.UsesTheServiceRoot;
import com.dispatchflow.archfixture.zeta.upper.application.service.used.UsedByTheServiceRoot;
import com.dispatchflow.archfixture.zeta.web.WebBindsTheCurrentCaller;
import com.dispatchflow.archfixture.zeta.web.WebReadsAModulesDomain;
import com.dispatchflow.archfixture.zeta.web.WebUsesAModulesAdapter;
import com.dispatchflow.archfixture.zeta.web.WebUsesAModulesPort;
import com.dispatchflow.archfixture.zeta.web.WebUsesAModulesRoot;
import com.dispatchflow.archfixture.zeta.web.WebUsesAModulesSpi;
import com.dispatchflow.archfixture.zeta.web.WebUsesAStrayModule;
import com.dispatchflow.archfixture.zeta.web.ZetaWebEndpoint;
import com.dispatchflow.archfixture.zeta.web.ZetaWebHandler;
import com.dispatchflow.archfixture.zeta.web.security.SessionUsesTheService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ViolationHandler;
import java.util.ArrayList;
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
    // The fixtures' stand-in for the generated optimizer client, and the one module that solves.
    private static final String CLIENT = "shared.adapter.out.optimizer";
    private static final Set<String> SOLVING_MODULES = Set.of("upper");
    // The fixtures' stand-in for Access's CurrentActor.
    private static final Set<String> CALLS_HOLDERS = Set.of("upper.application.api.UpperCurrentCaller");

    private static ArchRule rule1() {
        return ApplicationRules.onlyAllowedDependencies(ROOT, FIXTURE_MODULE_ANNOTATIONS, SPI_IMPLEMENTERS);
    }

    private static ArchRule spiRule4(Map<String, String> implementers) {
        return ApplicationRules.eachSpiHasOneImplementationInItsArrowsModule(ROOT, implementers, NO_RENAMED_PORTS);
    }

    private static ArchRule rule12() {
        return ApplicationRules.generatedClientStaysIn(ROOT, CLIENT, SOLVING_MODULES);
    }

    private static ArchRule rule14() {
        return ApplicationRules.onlyTheWebReadsTheCallsHolders(ROOT, CALLS_HOLDERS);
    }

    // Rule 15's fixtures that are package-private, by name.
    private static final String PEER_CONFIGURATION = ROOT + ".peer.PeerConfiguration";
    private static final String SIDE_CONFIGURATION = ROOT + ".side.SideConfiguration";
    private static final String GATE_CONFIGURATION = ROOT + ".gate.GateConfiguration";
    private static final String SCANNING_CONFIGURATION = ROOT + ".gate.ScanningConfiguration";
    private static final String SCANS_CONFIGURATION = ROOT + ".gate.ScansConfiguration";
    private static final String RESOURCE_CONFIGURATION = ROOT + ".gate.ResourceConfiguration";
    private static final String META_IMPORT_CONFIGURATION = ROOT + ".gate.MetaImportConfiguration";
    private static final String LOWER_CONFIGURATION = ROOT + ".lower.LowerConfiguration";
    private static final String SIBLING_IMPORT_CONFIGURATION = ROOT + ".gate.SiblingImportConfiguration";
    private static final String IMPORTS_A_USE_CASE = ROOT + ".ZetaImportsAUseCase";
    private static final String IMPORTS_THROUGH_AN_ANNOTATION = ROOT + ".ZetaImportsAUseCaseThroughAnAnnotation";
    private static final String MAKES_A_USE_CASE = ROOT + ".ZetaMakesAUseCase";

    private static ArchRule rule15Elsewhere() {
        return ApplicationRules.onlyTheModulesConfigurationsWireTheirUseCases(ROOT);
    }

    private static ArchRule rule16() {
        return ApplicationRules.noHandlerTakesAStreamingParameter(ROOT);
    }

    private static ArchRule rule15() {
        return ApplicationRules.moduleRootsHoldOnlyTheirConfiguration(ROOT);
    }

    private static JavaClasses named(String... names) {
        Set<String> wanted = Set.of(names);
        return zeta().that(DescribedPredicate.describe("one of " + wanted, type -> wanted.contains(type.getName())));
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

    /** Every message a rule reports, one per violation. */
    private static List<String> violationMessages(ArchRule rule, JavaClasses classes) {
        List<String> messages = new ArrayList<>();
        ViolationHandler<JavaClass> collect = (violating, message) -> messages.add(message);
        rule.evaluate(classes).handleViolations(collect);
        return messages;
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
                        DependsOnTheKernelsAdapter.class.getName(),
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
    void GivenAUseCaseThatDependsOnTheKernelsSharedAdapter_WhenRule1Evaluates_ThenItIsFlagged() {
        assertThat(violators(rule1(), FixtureClasses.only(DependsOnTheKernelsAdapter.class)))
                .containsExactly(DependsOnTheKernelsAdapter.class.getName());
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
                .containsExactlyInAnyOrder(namesOf(
                        LowerReachesUp.class,
                        LowerAdapterReachesUp.class,
                        StrayValue.class,
                        ControllerReachesTheWeb.class));
    }

    @Test
    void GivenAModulesControllerUsingAWebClass_WhenRule2Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH),
                        FixtureClasses.only(ControllerReachesTheWeb.class)))
                .containsExactly(ControllerReachesTheWeb.class.getName());
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
                        ROOT + ".gate.application.serviceold",
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
                        ROOT + ".upper.application.port.out.nested",
                        ROOT + ".upper.application.port.out.nested.below",
                        ROOT + ".upper.application.port.out.used",
                        ROOT + ".upper.application.service",
                        ROOT + ".upper.application.service.looped",
                        ROOT + ".upper.application.service.nested",
                        ROOT + ".upper.application.service.nested.below",
                        ROOT + ".upper.application.service.prefix",
                        ROOT + ".upper.application.service.prefixed",
                        ROOT + ".upper.application.service.upward",
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
    void
            GivenTheZetaFixtures_WhenRule7ChecksEachApplicationPackage_ThenExactlyThoseUsingTheirOwnSubpackageAreFlagged() {
        assertThat(violators(ApplicationRules.applicationPackagesUseNoSubpackage(ROOT), zeta()))
                .containsExactlyInAnyOrder(namesOf(
                        ServiceRootUsingASubpackage.class,
                        PortRootUsingASubpackage.class,
                        NestedPackageUsingASubpackage.class,
                        NestedPortUsingASubpackage.class));
    }

    @Test
    void
            GivenPackagesUsingThemselvesThePackageAboveOrOtherLayers_WhenRule7ChecksEachApplicationPackage_ThenNothingIsFlagged() {
        assertThat(violators(
                        ApplicationRules.applicationPackagesUseNoSubpackage(ROOT),
                        FixtureClasses.only(
                                UpperUseCase.class,
                                UpperRecords.class,
                                LoopedUseCase.class,
                                UsesTheServiceRoot.class,
                                UsesASiblingWithALongerName.class,
                                SiblingWithALongerName.class)))
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

    @Test
    void GivenTheZetaFixtures_WhenRule9Evaluates_ThenExactlyTheAdaptersUsingAnotherAdapterOfTheirModuleAreFlagged() {
        assertThat(violators(ApplicationRules.adaptersOfAModuleStayApart(ROOT), zeta()))
                .containsExactlyInAnyOrder(namesOf(
                        InboundUsesTheStore.class,
                        MailUsesTheStore.class,
                        PushReadsTheRequest.class,
                        ListenerUsesTheWebAdapter.class,
                        UpperOutboxUsesTheMail.class,
                        RelayUsesTheCommon.class,
                        ClockReadsThePersistence.class,
                        FormUsesTheRows.class,
                        RowReadsTheRequest.class));
    }

    @Test
    void GivenAnInboundAdapterUsingAnOutboundOne_WhenRule9Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersOfAModuleStayApart(ROOT),
                        FixtureClasses.only(InboundUsesTheStore.class)))
                .containsExactly(InboundUsesTheStore.class.getName());
    }

    @Test
    void GivenAnOutboundAdapterUsingAnotherOutboundOne_WhenRule9Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersOfAModuleStayApart(ROOT), FixtureClasses.only(MailUsesTheStore.class)))
                .containsExactly(MailUsesTheStore.class.getName());
    }

    @Test
    void GivenAnOutboundAdapterUsingAnInboundOne_WhenRule9Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersOfAModuleStayApart(ROOT),
                        FixtureClasses.only(PushReadsTheRequest.class)))
                .containsExactly(PushReadsTheRequest.class.getName());
    }

    @Test
    void GivenAnInboundAdapterUsingAnotherInboundOne_WhenRule9Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersOfAModuleStayApart(ROOT),
                        FixtureClasses.only(ListenerUsesTheWebAdapter.class)))
                .containsExactly(ListenerUsesTheWebAdapter.class.getName());
    }

    @Test
    void GivenAClassDirectlyInAdapterOutUsingAnAdapterBelowIt_WhenRule9Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersOfAModuleStayApart(ROOT),
                        FixtureClasses.only(UpperOutboxUsesTheMail.class)))
                .containsExactly(UpperOutboxUsesTheMail.class.getName());
    }

    @Test
    void GivenAnAdapterUsingAPackageUnderItsModulesAdapterOutsideInAndOut_WhenRule9Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersOfAModuleStayApart(ROOT),
                        FixtureClasses.only(RelayUsesTheCommon.class)))
                .containsExactly(RelayUsesTheCommon.class.getName());
    }

    @Test
    void GivenAClassBelowOneAdapterUsingAClassBelowAnother_WhenRule9Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersOfAModuleStayApart(ROOT),
                        FixtureClasses.only(FormUsesTheRows.class, UpperRow.class)))
                .containsExactly(FormUsesTheRows.class.getName());
    }

    @Test
    void GivenAClassBelowAnOutboundAdapterUsingAnInboundOne_WhenRule9Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersOfAModuleStayApart(ROOT),
                        FixtureClasses.only(RowReadsTheRequest.class)))
                .containsExactly(RowReadsTheRequest.class.getName());
    }

    @Test
    void GivenAnAdapterUsingAPackageUnderAnotherModulesAdapterOutsideInAndOut_WhenRule10Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT),
                        FixtureClasses.only(RelayUsesLowersCommon.class)))
                .containsExactly(RelayUsesLowersCommon.class.getName());
    }

    @Test
    void GivenASharedAdapterUsingAnotherSharedAdapter_WhenRule9Evaluates_ThenItIsFlagged() {
        JavaClasses clock = FixtureClasses.only(ClockReadsThePersistence.class);

        assertThat(violators(ApplicationRules.adaptersOfAModuleStayApart(ROOT), clock))
                .containsExactly(ClockReadsThePersistence.class.getName());
        assertThat(violators(ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT), clock))
                .isEmpty();
    }

    @Test
    void GivenAnAdapterUsingItsOwnSubpackage_WhenRule9Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersOfAModuleStayApart(ROOT),
                        FixtureClasses.only(UpperTable.class, UpperRow.class)))
                .isEmpty();
    }

    @Test
    void GivenAnAdapterUsingTheSharedPersistenceAdapter_WhenRules9And10Evaluate_ThenNothingIsFlagged() {
        JavaClasses store = FixtureClasses.only(StoreSavesThroughTheKernel.class);

        assertThat(violators(ApplicationRules.adaptersOfAModuleStayApart(ROOT), store))
                .isEmpty();
        assertThat(violators(ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT), store))
                .isEmpty();
    }

    @Test
    void GivenAnAdapterUsingASharedAdapterOtherThanPersistence_WhenRules9And10Evaluate_ThenNothingIsFlagged() {
        JavaClasses mail = FixtureClasses.only(MailReadsTheClock.class, ZetaClock.class);

        assertThat(violators(ApplicationRules.adaptersOfAModuleStayApart(ROOT), mail))
                .isEmpty();
        assertThat(violators(ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT), mail))
                .isEmpty();
    }

    @Test
    void GivenTheZetaFixtures_WhenRule10Evaluates_ThenExactlyTheAdaptersUsingAnotherModulesAdapterAreFlagged() {
        assertThat(violators(ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT), zeta()))
                .containsExactlyInAnyOrder(namesOf(
                        MirrorsTheLowerCache.class,
                        RelayUsesLowersCommon.class,
                        SharedClockReadsAModulesStore.class,
                        MirrorsADeepEntry.class,
                        WebUsesTheLowerCache.class,
                        MirrorsTheLowerForm.class));
    }

    @Test
    void GivenAnAdapterUsingAnotherModulesAdapterAlongAnArrow_WhenRule10Evaluates_ThenItIsFlaggedThoughRule2AllowsIt() {
        JavaClasses mirror = FixtureClasses.only(MirrorsTheLowerCache.class);

        assertThat(violators(ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH), mirror))
                .isEmpty();
        assertThat(violators(ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT), mirror))
                .containsExactly(MirrorsTheLowerCache.class.getName());
    }

    @Test
    void GivenAnInboundAdapterUsingAnotherModulesAdapter_WhenRule10Evaluates_ThenItIsFlaggedThoughRule2AllowsIt() {
        JavaClasses web = FixtureClasses.only(WebUsesTheLowerCache.class);

        assertThat(violators(ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH), web))
                .isEmpty();
        assertThat(violators(ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT), web))
                .containsExactly(WebUsesTheLowerCache.class.getName());
    }

    @Test
    void GivenAnAdapterUsingAnotherModulesInboundAdapter_WhenRule10Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT),
                        FixtureClasses.only(MirrorsTheLowerForm.class, LowerForm.class)))
                .containsExactly(MirrorsTheLowerForm.class.getName());
    }

    @Test
    void GivenAClassBelowAnAdapterUsingAClassBelowAnotherModulesAdapter_WhenRule10Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT),
                        FixtureClasses.only(MirrorsADeepEntry.class, LowerEntry.class)))
                .containsExactly(MirrorsADeepEntry.class.getName());
    }

    @Test
    void GivenASharedAdapterUsingAModulesAdapter_WhenRule10Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT),
                        FixtureClasses.only(SharedClockReadsAModulesStore.class)))
                .containsExactly(SharedClockReadsAModulesStore.class.getName());
    }

    @Test
    void GivenASharedAdapterUsingTheWebsAdapter_WhenRules9To11Evaluate_ThenOnlyRule11FlagsIt() {
        JavaClasses clock = FixtureClasses.only(ClockUsesTheWebStreams.class);

        assertThat(violators(ApplicationRules.adaptersOfAModuleStayApart(ROOT), clock))
                .isEmpty();
        assertThat(violators(ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT), clock))
                .isEmpty();
        assertThat(violators(ApplicationRules.sharedAdaptersUseNoModule(ROOT), clock))
                .containsExactly(ClockUsesTheWebStreams.class.getName());
    }

    @Test
    void GivenTheZetaFixtures_WhenRule11Evaluates_ThenExactlyTheSharedAdaptersUsingAModuleAreFlagged() {
        assertThat(violators(ApplicationRules.sharedAdaptersUseNoModule(ROOT), zeta()))
                .containsExactlyInAnyOrder(namesOf(
                        ClockReadsAModulesPort.class,
                        SharedClockReadsAModulesStore.class,
                        ClockUsesTheWebStreams.class,
                        ClockReadsAModulesDomain.class,
                        ClockHearsAModulesEvent.class,
                        SystemClockReadsAModule.class));
    }

    @Test
    void GivenASharedAdapterUsingAModulesPort_WhenRule11Evaluates_ThenItIsFlaggedThoughRules9And10AllowIt() {
        JavaClasses clock = FixtureClasses.only(ClockReadsAModulesPort.class);

        assertThat(violators(ApplicationRules.adaptersOfAModuleStayApart(ROOT), clock))
                .isEmpty();
        assertThat(violators(ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT), clock))
                .isEmpty();
        assertThat(violators(ApplicationRules.sharedAdaptersUseNoModule(ROOT), clock))
                .containsExactly(ClockReadsAModulesPort.class.getName());
    }

    @Test
    void GivenASharedAdapterUsingTheKernelsAdapters_WhenRule11Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(
                        ApplicationRules.sharedAdaptersUseNoModule(ROOT),
                        FixtureClasses.only(ClockReadsThePersistence.class)))
                .isEmpty();
    }

    @Test
    void GivenSharedAdaptersUsingAModulesDomainAndItsEvent_WhenRule11Evaluates_ThenBothAreFlagged() {
        assertThat(violators(
                        ApplicationRules.sharedAdaptersUseNoModule(ROOT),
                        FixtureClasses.only(ClockReadsAModulesDomain.class, ClockHearsAModulesEvent.class)))
                .containsExactlyInAnyOrder(namesOf(ClockReadsAModulesDomain.class, ClockHearsAModulesEvent.class));
    }

    @Test
    void GivenAClassBelowASharedAdapterUsingAModule_WhenRule11Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.sharedAdaptersUseNoModule(ROOT),
                        FixtureClasses.only(SystemClockReadsAModule.class)))
                .containsExactly(SystemClockReadsAModule.class.getName());
    }

    @Test
    void
            GivenTheZetaFixtures_WhenRule12Evaluates_ThenExactlyTheClientsUsersOutsideASolvingModulesOptimizerAreFlagged() {
        assertThat(violators(rule12(), zeta()))
                .containsExactlyInAnyOrder(namesOf(WebCallsTheSolverClient.class, LowerOptimizer.class));
    }

    @Test
    void GivenTheOptimizerAdapterOfASolvingModule_WhenRule12Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(rule12(), FixtureClasses.only(UpperOptimizer.class, ZetaSolverClient.class)))
                .isEmpty();
    }

    @Test
    void GivenAnotherAdapterOfASolvingModuleUsingTheClient_WhenRule12Evaluates_ThenItIsFlagged() {
        assertThat(violators(rule12(), FixtureClasses.only(WebCallsTheSolverClient.class)))
                .containsExactly(WebCallsTheSolverClient.class.getName());
    }

    @Test
    void GivenTheOptimizerAdapterOfAModuleThatDoesNotSolve_WhenRule12Evaluates_ThenItIsFlagged() {
        assertThat(violators(rule12(), FixtureClasses.only(LowerOptimizer.class)))
                .containsExactly(LowerOptimizer.class.getName());
    }

    @Test
    void
            GivenTheZetaFixtures_WhenRule13Evaluates_ThenExactlyTheWebClassesUsingMoreThanTheModulesApiAndEventsAreFlagged() {
        assertThat(violators(ApplicationRules.webUsesOnlyTheModulesApiAndEvents(ROOT), zeta()))
                .containsExactlyInAnyOrder(namesOf(
                        ZetaWebHandler.class,
                        WebReadsAModulesDomain.class,
                        WebUsesAModulesSpi.class,
                        WebUsesAModulesPort.class,
                        WebUsesAModulesAdapter.class,
                        WebUsesAModulesRoot.class,
                        WebUsesAStrayModule.class,
                        SessionUsesTheService.class));
    }

    @Test
    void GivenWebClassesUsingAModulesDomainSpiPortAdapterAndRoot_WhenRule13Evaluates_ThenEachIsFlagged() {
        assertThat(violators(
                        ApplicationRules.webUsesOnlyTheModulesApiAndEvents(ROOT),
                        FixtureClasses.only(
                                WebReadsAModulesDomain.class,
                                WebUsesAModulesSpi.class,
                                WebUsesAModulesPort.class,
                                WebUsesAModulesAdapter.class,
                                WebUsesAModulesRoot.class)))
                .containsExactlyInAnyOrder(namesOf(
                        WebReadsAModulesDomain.class,
                        WebUsesAModulesSpi.class,
                        WebUsesAModulesPort.class,
                        WebUsesAModulesAdapter.class,
                        WebUsesAModulesRoot.class));
    }

    @Test
    void GivenAWebClassUsingAModuleMissingFromTheGraph_WhenRule13Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.webUsesOnlyTheModulesApiAndEvents(ROOT),
                        FixtureClasses.only(WebUsesAStrayModule.class)))
                .containsExactly(WebUsesAStrayModule.class.getName());
    }

    @Test
    void GivenAClassBelowTheWebUsingAModulesService_WhenRule13Evaluates_ThenItIsFlagged() {
        assertThat(violators(
                        ApplicationRules.webUsesOnlyTheModulesApiAndEvents(ROOT),
                        FixtureClasses.only(SessionUsesTheService.class)))
                .containsExactly(SessionUsesTheService.class.getName());
    }

    @Test
    void GivenAWebClassUsingTheModulesServices_WhenRule13Evaluates_ThenItIsFlaggedThoughRule2AllowsIt() {
        JavaClasses handler = FixtureClasses.only(ZetaWebHandler.class);

        assertThat(violators(ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH), handler))
                .isEmpty();
        assertThat(violators(ApplicationRules.webUsesOnlyTheModulesApiAndEvents(ROOT), handler))
                .containsExactly(ZetaWebHandler.class.getName());
    }

    @Test
    void GivenAWebClassUsingAnApiAnEventTheKernelAKernelAdapterAndTheWeb_WhenRule13Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(
                        ApplicationRules.webUsesOnlyTheModulesApiAndEvents(ROOT),
                        FixtureClasses.only(ZetaWebEndpoint.class)))
                .isEmpty();
    }

    @Test
    void GivenTheZetaFixtures_WhenRule14Evaluates_ThenExactlyTheClassesOutsideTheWebReadingTheHolderAreFlagged() {
        assertThat(violators(rule14(), zeta()))
                .containsExactlyInAnyOrder(
                        namesOf(ServiceReadsTheCurrentCaller.class, ListenerReadsTheCurrentCaller.class));
    }

    @Test
    void GivenAUseCaseReadingTheHolder_WhenRule14Evaluates_ThenItIsFlaggedThoughRule1AllowsIt() {
        JavaClasses service = FixtureClasses.only(ServiceReadsTheCurrentCaller.class);

        assertThat(violators(rule1(), service)).isEmpty();
        assertThat(violators(rule14(), service)).containsExactly(ServiceReadsTheCurrentCaller.class.getName());
    }

    @Test
    void GivenAListenerAdapterReadingTheHolder_WhenRule14Evaluates_ThenItIsFlagged() {
        assertThat(violators(rule14(), FixtureClasses.only(ListenerReadsTheCurrentCaller.class)))
                .containsExactly(ListenerReadsTheCurrentCaller.class.getName());
    }

    @Test
    void GivenTheHolderAModulesControllerAndAWebClassReadingIt_WhenRule14Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(
                        rule14(),
                        FixtureClasses.only(
                                UpperCurrentCaller.class,
                                ControllerReadsTheCurrentCaller.class,
                                WebBindsTheCurrentCaller.class)))
                .isEmpty();
    }

    @Test
    void GivenHoldersThatNoClassIsYet_WhenRule14Evaluates_ThenItChecksTheClassesAndFlagsNothing() {
        assertThat(ApplicationRules.onlyTheWebReadsTheCallsHolders(ROOT)
                        .evaluate(zeta())
                        .hasViolation())
                .isFalse();
    }

    @Test
    void GivenTheZetaFixtures_WhenTheAdapterNullMarkedGuardChecks_ThenTheMissingAndTheUnmarkedPackageInfoAreReported() {
        assertThat(ApplicationRules.adapterPackagesNotNullMarked(ROOT, zeta()))
                .containsExactly(
                        ROOT + ".lower.adapter.in.web",
                        ROOT + ".lower.adapter.out",
                        ROOT + ".report.adapter.out",
                        ROOT + ".shared.adapter.out.persistence",
                        ROOT + ".upper.adapter.out");
    }

    @Test
    void GivenTheZetaFixtures_WhenAdapterNullMarkedSubjectsAreListed_ThenEveryAdapterPackageAndNoOtherIsAmongThem() {
        assertThat(ApplicationRules.nullMarkedAdapterSubjects(ROOT, zeta()))
                .containsExactly(
                        ROOT + ".lower.adapter.common",
                        ROOT + ".lower.adapter.in.web",
                        ROOT + ".lower.adapter.out",
                        ROOT + ".lower.adapter.out.cache",
                        ROOT + ".lower.adapter.out.cache.deep",
                        ROOT + ".lower.adapter.out.optimizer",
                        ROOT + ".report.adapter.out",
                        ROOT + ".shared.adapter.out.clock",
                        ROOT + ".shared.adapter.out.clock.system",
                        ROOT + ".shared.adapter.out.optimizer",
                        ROOT + ".shared.adapter.out.persistence",
                        ROOT + ".upper.adapter.common",
                        ROOT + ".upper.adapter.in.listen",
                        ROOT + ".upper.adapter.in.web",
                        ROOT + ".upper.adapter.in.web.form",
                        ROOT + ".upper.adapter.out",
                        ROOT + ".upper.adapter.out.mail",
                        ROOT + ".upper.adapter.out.mirror",
                        ROOT + ".upper.adapter.out.mirror.deep",
                        ROOT + ".upper.adapter.out.optimizer",
                        ROOT + ".upper.adapter.out.push",
                        ROOT + ".upper.adapter.out.relay",
                        ROOT + ".upper.adapter.out.store",
                        ROOT + ".upper.adapter.out.store.rows",
                        ROOT + ".web.adapter.out.streams");
    }

    @Test
    void
            GivenTheZetaFixtures_WhenTheirAdaptersAreListed_ThenEachTopLevelPackageNamesItsAdaptersAndAClassDirectlyInADirection() {
        assertThat(ApplicationRules.adaptersByTopLevelPackage(ROOT, zeta()))
                .isEqualTo(Map.of(
                        "lower",
                        Set.of("common", "in.web", "out", "out.cache", "out.optimizer"),
                        "report",
                        Set.of("out"),
                        "shared",
                        Set.of("out.clock", "out.optimizer", "out.persistence"),
                        "upper",
                        Set.of(
                                "common",
                                "in.listen",
                                "in.web",
                                "out",
                                "out.mail",
                                "out.mirror",
                                "out.optimizer",
                                "out.push",
                                "out.relay",
                                "out.store"),
                        "web",
                        Set.of("out.streams")));
    }

    @Test
    void GivenTheZetaFixtures_WhenRule15Evaluates_ThenExactlyTheRootsOtherClassesAndBadConfigurationsAreFlagged() {
        assertThat(violators(rule15(), zeta()))
                .containsExactlyInAnyOrder(
                        LowerRootValue.class.getName(),
                        StrayValue.class.getName(),
                        SIDE_CONFIGURATION,
                        UpperConfiguration.class.getName(),
                        GATE_CONFIGURATION,
                        SCANNING_CONFIGURATION,
                        SCANS_CONFIGURATION,
                        RESOURCE_CONFIGURATION,
                        META_IMPORT_CONFIGURATION,
                        LOWER_CONFIGURATION,
                        SIBLING_IMPORT_CONFIGURATION);
    }

    @Test
    void GivenAConfigurationImportingItsOwnModulesAdapterAndDomain_WhenTheConfigurationRuleRuns_ThenItIsReported() {
        assertThat(violators(rule15(), named(LOWER_CONFIGURATION))).containsExactly(LOWER_CONFIGURATION);
    }

    @Test
    void
            GivenAConfigurationImportingASubpackageOfItsOwnService_WhenTheConfigurationRuleRuns_ThenOnlyBeingPublicIsReported() {
        List<String> reported = violationMessages(rule15(), FixtureClasses.only(UpperConfiguration.class));

        assertThat(reported).singleElement().asString().endsWith(" is public");
    }

    @Test
    void GivenAConfigurationImportingFromASiblingNamedLikeItsService_WhenTheConfigurationRuleRuns_ThenItIsReported() {
        assertThat(violators(rule15(), named(SIBLING_IMPORT_CONFIGURATION)))
                .containsExactly(SIBLING_IMPORT_CONFIGURATION);
    }

    @Test
    void GivenAConfigurationThatScansForComponents_WhenTheConfigurationRuleRuns_ThenItIsReported() {
        assertThat(violators(rule15(), named(SCANNING_CONFIGURATION))).containsExactly(SCANNING_CONFIGURATION);
    }

    @Test
    void GivenAConfigurationThatScansThroughComponentScans_WhenTheConfigurationRuleRuns_ThenItIsReported() {
        assertThat(violators(rule15(), named(SCANS_CONFIGURATION))).containsExactly(SCANS_CONFIGURATION);
    }

    @Test
    void GivenAConfigurationThatImportsAResource_WhenTheConfigurationRuleRuns_ThenItIsReported() {
        assertThat(violators(rule15(), named(RESOURCE_CONFIGURATION))).containsExactly(RESOURCE_CONFIGURATION);
    }

    @Test
    void GivenAConfigurationWhoseImportArrivesThroughAnAnnotation_WhenTheConfigurationRuleRuns_ThenItIsReported() {
        assertThat(violators(rule15(), named(META_IMPORT_CONFIGURATION))).containsExactly(META_IMPORT_CONFIGURATION);
    }

    @Test
    void GivenTheZetaFixtures_WhenRule15ChecksTheRestOfTheTree_ThenExactlyWhatWiresAUseCaseOutsideARootIsFlagged() {
        assertThat(violators(rule15Elsewhere(), zeta()))
                .containsExactlyInAnyOrder(
                        ImportsPeersUseCase.class.getName(),
                        IMPORTS_A_USE_CASE,
                        IMPORTS_THROUGH_AN_ANNOTATION,
                        MAKES_A_USE_CASE);
    }

    @Test
    void GivenAModulesRootConfigurations_WhenRule15ChecksTheRestOfTheTree_ThenNoneIsFlagged() {
        assertThat(violators(rule15Elsewhere(), named(PEER_CONFIGURATION, SIDE_CONFIGURATION)))
                .isEmpty();
    }

    @Test
    void GivenABeanMethodThatMakesAUseCaseBehindItsApi_WhenRule15ChecksTheRestOfTheTree_ThenItIsReported() {
        assertThat(violators(rule15Elsewhere(), named(MAKES_A_USE_CASE))).containsExactly(MAKES_A_USE_CASE);
    }

    @Test
    void GivenTheZetaFixtures_WhenRule16Evaluates_ThenExactlyTheHandlersThatTakeAStreamAreFlagged() {
        assertThat(violators(rule16(), zeta()))
                .containsExactlyInAnyOrder(
                        ControllerTakesAStream.class.getName(),
                        ControllerTakesAReader.class.getName(),
                        ControllerTakesTheRequest.class.getName(),
                        ControllerTakesAnOutputStream.class.getName(),
                        ControllerTakesAWriter.class.getName(),
                        ControllerTakesAStreamedResource.class.getName(),
                        HandlerWritesTheResponse.class.getName());
    }

    @Test
    void GivenAControllerWhoseHandlerTakesWhatAConverterRead_WhenRule16Evaluates_ThenNothingIsFlagged() {
        assertThat(violators(rule16(), FixtureClasses.only(ControllerTakesItsArguments.class)))
                .isEmpty();
    }

    @Test
    void GivenAPlainClassWhoseMethodTakesAStream_WhenRule16Evaluates_ThenItIsLeftAlone() {
        assertThat(violators(rule16(), FixtureClasses.only(PlainClassTakesAStream.class)))
                .isEmpty();
    }

    @Test
    void GivenAModuleRootHoldingAnotherClass_WhenTheConfigurationRuleRuns_ThenItIsReported() {
        assertThat(violators(rule15(), FixtureClasses.only(LowerRootValue.class)))
                .containsExactly(LowerRootValue.class.getName());
    }

    @Test
    void GivenAConfigurationImportingAnotherModulesService_WhenTheConfigurationRuleRuns_ThenItIsReported() {
        JavaClasses side = named(SIDE_CONFIGURATION);

        assertThat(violators(ApplicationRules.modulesDependAlongTheGraph(ROOT, GRAPH), side))
                .isEmpty();
        assertThat(violators(rule15(), side)).containsExactly(SIDE_CONFIGURATION);
    }

    @Test
    void GivenAPublicConfiguration_WhenTheConfigurationRuleRuns_ThenItIsReported() {
        assertThat(violators(rule15(), FixtureClasses.only(UpperConfiguration.class)))
                .containsExactly(UpperConfiguration.class.getName());
    }

    @Test
    void GivenAConfigurationWithABeanMethod_WhenTheConfigurationRuleRuns_ThenItIsReported() {
        assertThat(violators(rule15(), named(GATE_CONFIGURATION))).containsExactly(GATE_CONFIGURATION);
    }

    @Test
    void GivenAConfigurationImportingItsOwnService_WhenTheConfigurationRuleRuns_ThenNothingIsFlagged() {
        JavaClasses peer = named(PEER_CONFIGURATION);

        assertThat(peer.stream().map(JavaClass::getName)).containsExactly(PEER_CONFIGURATION);
        assertThat(violators(rule15(), peer)).isEmpty();
    }
}
