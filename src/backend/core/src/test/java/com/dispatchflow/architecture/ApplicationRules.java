package com.dispatchflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

/**
 * The application layer's rules (TS §2.2, TS §4, DL §2.1, DL §2.3, ADR-120), the adapters' (TS
 * §2.1, ADR-130), {@code web}'s (rules 13 and 14, ADR-140), the modules' configurations' (rule
 * 15, ADR-140 decision 10) and the handlers' (rule 16, ADR-142), built for a root package as {@link
 * DomainPurityRules} is. {@code com.dispatchflow} checks production code; {@code
 * com.dispatchflow.archfixture.zeta} checks the fixtures that prove each rule fires.
 *
 * <p>Every violation is reported against the class that breaks the rule, so a test can name the
 * exact violating classes.
 */
final class ApplicationRules {

    private static final String KERNEL = "shared";
    private static final String WEB = "web";
    private static final String DOMAIN = "domain";
    private static final String EVENTS = "events";
    private static final String APPLICATION = "application";
    private static final String API = "api";
    private static final String SPI = "spi";
    private static final String ADAPTER = "adapter";
    private static final String IN = "in";
    private static final String OUT = "out";
    // An adapter is named by its module, "adapter", its direction and its own name.
    private static final int ADAPTER_SEGMENTS = 4;
    private static final String SERVICE = "service";
    private static final String SERVICE_SUFFIX = "Service";
    private static final String PACKAGE_INFO = "package-info";
    private static final String CONFIGURATION_SUFFIX = "Configuration";
    // Read by name, so the rule needs no Spring type of its own: ArchUnit reads @Import's classes as
    // JavaClass values without loading them.
    private static final String BEAN = "org.springframework.context.annotation.Bean";
    private static final String COMPONENT_SCAN = "org.springframework.context.annotation.ComponentScan";
    private static final String IMPORT = "org.springframework.context.annotation.Import";
    private static final String IMPORT_RESOURCE = "org.springframework.context.annotation.ImportResource";
    private static final String COMPONENT_SCANS = "org.springframework.context.annotation.ComponentScans";
    private static final String IMPORTED = "value";
    private static final String CONTROLLER = "org.springframework.stereotype.Controller";
    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";

    /**
     * The parameter types no handler declares (rule 16), each with its subtypes: each reads the
     * request's body or writes the response itself, past the converters, or, as an {@code
     * InputStreamResource} does, hands over a live stream a retry would find read. The rule reads a
     * parameter's raw type, so a type argument, as in {@code HttpEntity<InputStream>}, is review's.
     */
    static final List<String> STREAMING_TYPES = List.of(
            "java.io.InputStream",
            "java.io.Reader",
            "java.io.OutputStream",
            "java.io.Writer",
            "jakarta.servlet.ServletRequest",
            "jakarta.servlet.ServletResponse",
            "org.springframework.core.io.InputStreamResource");

    private static final List<String> ALWAYS_ALLOWED = List.of("java", "org.jspecify");
    // TS §2.2: the one Spring annotation the application may carry, so a use case can mark its
    // transaction. ArchUnit counts an annotation's members, defaults included, as dependencies, so
    // even a bare @Transactional names Propagation and Isolation; ADR-110's REQUIRES_NEW needs them
    // too.
    private static final Set<String> TRANSACTIONAL = Set.of(
            "org.springframework.transaction.annotation.Transactional",
            "org.springframework.transaction.annotation.Propagation",
            "org.springframework.transaction.annotation.Isolation");
    // Once Spring Modulith is added, @ApplicationModule and @NamedInterface go on these packages'
    // package-info (ADR-040).
    private static final String MODULITH = "org.springframework.modulith";

    /**
     * DL §2.1's arrows: each module, and the modules it may depend on. Every module may also depend
     * on the kernel, which is no module. A module missing here fails {@link
     * #modulesDependAlongTheGraph}, so a new module joins the graph on purpose.
     */
    static final Map<String, Set<String>> MODULE_GRAPH = Map.of(
            "audit", Set.of(),
            "access", Set.of("audit"),
            "fleet", Set.of("access", "audit"),
            "orders", Set.of("access", "audit"),
            "planning", Set.of("fleet", "orders", "access", "audit"),
            "liveops", Set.of("planning", "orders", "fleet", "access", "audit"),
            "messaging", Set.of("liveops", "planning", "orders", "access", "audit"),
            "reporting", Set.of("liveops", "planning", "orders", "access", "audit"));

    /**
     * DL §2.1's dashed arrows: each {@code spi} interface, by its name below the root, and the
     * module that implements it. An interface missing here fails {@link
     * #eachSpiHasOneImplementationInItsArrowsModule}, so a new {@code spi} joins the context map on
     * purpose.
     */
    static final Map<String, String> SPI_IMPLEMENTERS = Map.of(
            "planning.application.spi.ExecutionFacts", "liveops",
            "orders.application.spi.StopProgress", "liveops",
            "orders.application.spi.OrderPlacement", "planning",
            "orders.application.spi.RecordUsage", "planning",
            "fleet.application.spi.RecordUsage", "planning");

    /**
     * ADR-120 decision 5's renamed implementations: each {@code spi} interface, by its name below
     * the root, whose implementation is not its {@code XService}, and that implementation's simple
     * name. Both ports are named {@code RecordUsage} (P9), so one implementing module can't name
     * both {@code RecordUsageService}.
     */
    static final Map<String, String> SPI_IMPLEMENTATION_NAMES = Map.of(
            "fleet.application.spi.RecordUsage", "FleetUsageService",
            "orders.application.spi.RecordUsage", "CustomerUsageService");

    /** ADR-120 P5: the module that reads the others only through the views they publish. */
    static final String REPORTING = "reporting";

    /**
     * The modules Reporting reports on, whose {@code events} alone it may use (ADR-120 P5). Access
     * and Audit are not among them: Reporting takes the {@code Actor} and writes its {@code
     * AuditLog} records as every module does.
     */
    static final Set<String> REPORTING_SOURCES = Set.of("liveops", "planning", "orders");

    /**
     * The modules whose {@code api} Reporting may use only in part, and the types of it, by their
     * names below the root, that it may use: of Access, the {@code Actor} a web call acts for, its
     * roles and {@code CurrentActor}, which its controllers read, and nothing that reads an account
     * or the company (ADR-120 P5, ADR-140 decision 4).
     */
    static final Map<String, Set<String>> REPORTING_NARROWED_APIS = Map.of(
            "access",
            Set.of(
                    "access.application.api.Actor",
                    "access.application.api.ActorRole",
                    "access.application.api.CurrentActor"));

    /**
     * The top-level packages under the root that are no module: the kernel, below every module, and
     * {@code web}, the app-wide web concerns, above them all. Rule 2 refuses every module a
     * dependency on {@code web}, and rule 13 limits {@code web} to the modules' {@code api} and
     * {@code events} (ADR-140).
     */
    static final Set<String> NOT_MODULES = Set.of(KERNEL, WEB);

    /**
     * ADR-130 decision 2: the kinds of inbound adapter, {@code <module>.adapter.in.<kind>}. A use
     * case starts from a screen ({@code web}), an event, its module's own or another's ({@code
     * listen}), the clock ({@code schedule}), queued work or the core's startup ({@code job},
     * ADR-133), an outside service calling in ({@code callback}) or a person at a prompt ({@code
     * cli}).
     */
    static final Set<String> INBOUND_KINDS = Set.of("web", "listen", "schedule", "job", "callback", "cli");

    /**
     * ADR-130 decision 4, ADR-131 and ADR-133: each module's adapters, and the kernel's, by their
     * names below its {@code adapter} package, such as {@code in.web} or {@code out.persistence}. A
     * subpackage of an adapter, such as {@code out.persistence.plan}, counts as that adapter. The
     * tree must hold exactly these, so a new adapter, or a dropped one, is declared here on
     * purpose, as a new module joins {@link #MODULE_GRAPH}. The kernel's {@code in.web}, the code
     * generated from {@code client-api}, and its {@code out.sse}, the registry of open event
     * streams, sit beside it so that the modules reach them without depending on {@code web}
     * (ADR-140).
     */
    static final Map<String, Set<String>> DECLARED_ADAPTERS = Map.of(
            "access",
            Set.of(
                    "in.web",
                    "in.listen",
                    "in.schedule",
                    "in.job",
                    "in.cli",
                    "out.persistence",
                    "out.email",
                    "out.crypto",
                    "out.resources"),
            "audit",
            Set.of("in.schedule", "in.cli", "out.persistence"),
            "fleet",
            Set.of("in.web", "in.listen", "in.schedule", "in.cli", "out.persistence", "out.govmap"),
            "orders",
            Set.of(
                    "in.web",
                    "in.schedule",
                    "in.job",
                    "in.cli",
                    "out.persistence",
                    "out.govmap",
                    "out.osrm",
                    "out.roaddata"),
            "planning",
            Set.of(
                    "in.web",
                    "in.listen",
                    "in.schedule",
                    "in.job",
                    "in.cli",
                    "out.persistence",
                    "out.optimizer",
                    "out.osrm",
                    "out.sse",
                    "out.random",
                    "out.config"),
            "liveops",
            Set.of(
                    "in.web",
                    "in.listen",
                    "in.schedule",
                    "in.cli",
                    "out.persistence",
                    "out.push",
                    "out.sse",
                    "out.disk"),
            "messaging",
            Set.of(
                    "in.web",
                    "in.listen",
                    "in.schedule",
                    "in.callback",
                    "in.cli",
                    "out.persistence",
                    "out.sms",
                    "out.crypto"),
            "reporting",
            Set.of("in.web", "in.listen", "in.cli", "out.persistence", "out.optimizer", "out.config"),
            KERNEL,
            Set.of("in.web", "out.persistence", "out.clock", "out.uuid", "out.optimizer", "out.sse"));

    /**
     * ADR-130 decision 5: the client generated from {@code optimization-api}, below the root. Only
     * the {@code optimizer} adapters of {@link #SOLVING_MODULES} may use it (rule 12).
     */
    static final String OPTIMIZER_CLIENT = KERNEL + "." + ADAPTER + "." + OUT + ".optimizer";

    /** The modules that call the optimizer (DL §2.1): Planning solves, Reporting plans its baseline. */
    static final Set<String> SOLVING_MODULES = Set.of("planning", REPORTING);

    /**
     * The holder of the signed-in caller's {@code Actor}, below the root: a {@code
     * ScopedValue<Actor>} that {@code web} binds around a signed-in call (ADR-140 decision 4).
     * Only {@code web} and the modules' {@code adapter.in.web} may use it (rule 14).
     */
    static final String CURRENT_ACTOR = "access.application.api.CurrentActor";

    /**
     * The holder of the call's {@code CallContext}, below the root: a {@code
     * ScopedValue<CallContext>} that {@code web} binds around every web call, signed in or not
     * (ADR-140). Only {@code web} and the modules' {@code adapter.in.web} may use it (rule 14).
     */
    static final String CURRENT_CALL = "audit.application.api.CurrentCall";

    /**
     * The holders of what a web call knows of its caller, which rule 14 keeps from the use cases
     * and rule 18 lets only the call wrapper bind. Both match them by name, so a holder built under
     * another name would pass them unseen: {@link #missingHolders} reports a name here that is no
     * production class (ADR-140 decision 4).
     */
    static final Set<String> CALLS_HOLDERS = Set.of(CURRENT_ACTOR, CURRENT_CALL);

    /** The signed-in caller's {@code Actor}, below the root, which only {@code web.security} builds (rule 17). */
    static final String ACTOR = "access.application.api.Actor";

    /** The call's {@code CallContext}, below the root, which no handler takes as a parameter (rule 21). */
    static final String CALL_CONTEXT = "audit.application.api.CallContext";

    /** The one class, below the root, that binds the call's holders (rule 18), with its nested classes. */
    static final String CALL_WRAPPER = "web.CallWrapper";

    /** The holders' one method that reads; every other binds (rule 18). */
    private static final String HOLDERS_READ = "get";

    /** The package of {@code web} that proves who the caller is (rule 17). */
    private static final String SECURITY = "security";

    private ApplicationRules() {}

    /**
     * Rule 1: each module's application layer and the shared application package depend only on
     * what TS §2.2 and DL §2.3 allow, with Spring Modulith's annotations allowed on their {@code
     * package-info} only. A module's own {@code adapter} is not among them, nor the kernel's
     * {@code shared.adapter} (ADR-126): dependencies point from the adapters to the application,
     * never back. An interface of another module's {@code spi}
     * that a dashed arrow of {@link #SPI_IMPLEMENTERS} names is open only to the module that arrow
     * names to implement it; the rest of that {@code spi}, the records beside the interfaces, is
     * open to every module an arrow names to implement one of its interfaces.
     *
     * <p>{@code @Transactional} is allowed here for a module's use cases. The shared application
     * package can't take it all the same: it sits under the kernel, so {@link DomainPurityRules}
     * holds it to the kernel's purity rules too, and they allow no Spring.
     */
    static ArchRule onlyAllowedDependencies(String root) {
        return onlyAllowedDependencies(root, MODULITH, SPI_IMPLEMENTERS);
    }

    /**
     * Rule 1, with the package whose annotations a {@code package-info} may carry given: Spring
     * Modulith's for production, a stand-in for the fixtures, because Modulith is not on the
     * classpath yet. The dashed arrows are given too, as rule 4 takes them.
     *
     * @param implementers each {@code spi} interface, by its name below the root, and the module
     *     that implements it
     */
    static ArchRule onlyAllowedDependencies(String root, String moduleAnnotations, Map<String, String> implementers) {
        return classes()
                .that()
                .resideInAPackage(applicationLayers(root))
                .should(dependOnlyOn(
                        "the packages TS §2.2 allows the application",
                        (item, target) ->
                                isAllowedInTheApplication(root, moduleAnnotations, implementers, item, target)))
                .because("TS §2.2 and DL §2.3: the application imports the JDK, JSpecify, the kernel, its own"
                        + " module, other modules' api and events, the spi interfaces whose dashed arrows it"
                        + " implements and the records beside them, and from Spring only @Transactional");
    }

    /** Rule 2: a module depends on another module only along DL §2.1's arrows. */
    static ArchRule modulesDependAlongTheGraph(String root) {
        return modulesDependAlongTheGraph(root, MODULE_GRAPH);
    }

    /**
     * Rule 2, with the graph given, so the fixtures can bring a graph of their own. Each class in a
     * top-level package under the root, other than the kernel and {@code web}, belongs to the
     * module of that name. A module missing from the graph fails for each of its classes.
     *
     * @param graph each module, and the modules it may depend on
     */
    static ArchRule modulesDependAlongTheGraph(String root, Map<String, Set<String>> graph) {
        return classes()
                .that()
                .resideInAPackage(root + ".*..")
                .should(new ArchCondition<>("depend on other modules only along the module graph") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        Optional<String> module =
                                topLevelPackage(root, item).filter(name -> !NOT_MODULES.contains(name));
                        if (module.isEmpty()) {
                            return;
                        }
                        Set<String> allowed = graph.get(module.get());
                        if (allowed == null) {
                            events.add(SimpleConditionEvent.violated(
                                    item,
                                    item.getName() + " is in module " + module.get()
                                            + ", which the module graph does not name"));
                            return;
                        }
                        item.getDirectDependenciesFromSelf().stream()
                                .filter(dependency -> topLevelPackage(root, baseType(dependency.getTargetClass()))
                                        .filter(target -> !target.equals(module.get())
                                                && !target.equals(KERNEL)
                                                && !allowed.contains(target))
                                        .isPresent())
                                .forEach(dependency ->
                                        events.add(SimpleConditionEvent.violated(item, dependency.getDescription())));
                    }
                })
                .because("DL §2.1: the context map's arrows are the only dependencies between modules");
    }

    /**
     * Rule 3: an {@code api} or {@code spi} package uses no domain type, its own module's included.
     */
    static ArchRule apiAndSpiUseNoDomainType(String root) {
        return classes()
                .that()
                .resideInAnyPackage(
                        root + ".*." + APPLICATION + "." + API + "..", root + ".*." + APPLICATION + "." + SPI + "..")
                .should(dependOnlyOn(
                        "no domain type", (item, target) -> !isInAModulesDomain(root, target.getPackageName())))
                .because("DL §2.3 rule 1: what crosses a module boundary is kernel and JDK types and the"
                        + " port's own records, never a domain type");
    }

    /**
     * Rule 4, for {@code api}: every interface in a module's {@code application.api} has exactly
     * one concrete implementation, {@code XService} for an interface {@code X}, under its own
     * module's {@code application.service}. A sealed interface whose permitted subclasses are all
     * records or enums models the variants of a fact, not a port, and is skipped.
     */
    static ArchRule eachApiHasOneImplementationInItsModule(String root) {
        return classes()
                .that()
                .resideInAPackage(root + ".*." + APPLICATION + "." + API + "..")
                .should(haveOneImplementation(
                        root, "its own module", item -> topLevelPackage(root, item), ApplicationRules::serviceNameOf))
                .because("ADR-120 decision 5: an api interface X is implemented by XService in its own module");
    }

    /**
     * Rule 4, for {@code spi}, with production's dashed arrows, {@link #SPI_IMPLEMENTERS}, and
     * production's renamed implementations, {@link #SPI_IMPLEMENTATION_NAMES}.
     */
    static ArchRule eachSpiHasOneImplementationInItsArrowsModule(String root) {
        return eachSpiHasOneImplementationInItsArrowsModule(root, SPI_IMPLEMENTERS, SPI_IMPLEMENTATION_NAMES);
    }

    /**
     * Rule 4, for {@code spi}, with the dashed arrows and the renamed implementations given, as
     * rule 2 takes its graph: every interface in a module's {@code application.spi} has exactly one
     * concrete implementation, under the {@code application.service} of the module its arrow names.
     * It is named {@code XService} for an interface {@code X}, or the name {@code names} gives it.
     * An interface with no arrow fails. A sealed interface over records and enums only is skipped,
     * as for {@code api}.
     *
     * @param implementers each {@code spi} interface, by its name below the root, and the module
     *     that implements it
     * @param names each {@code spi} interface, by its name below the root, whose implementation is
     *     not its {@code XService}, and that implementation's simple name
     */
    static ArchRule eachSpiHasOneImplementationInItsArrowsModule(
            String root, Map<String, String> implementers, Map<String, String> names) {
        return classes()
                .that()
                .resideInAPackage(root + ".*." + APPLICATION + "." + SPI + "..")
                .should(haveOneImplementation(
                        root,
                        "the module its dashed arrow names",
                        item -> Optional.ofNullable(implementers.get(nameBelow(root, item))),
                        item -> names.getOrDefault(nameBelow(root, item), serviceNameOf(item))))
                .because("DL §2.1, DL §2.3 and ADR-120 decision 5: a module owns an spi for exactly the one"
                        + " module its dashed arrow names to implement, by XService, or by FleetUsageService and"
                        + " CustomerUsageService for the two RecordUsage ports");
    }

    /**
     * Rule 8, for production: Reporting uses only the {@code events} of the modules it reports on,
     * and of Access's {@code api} only the types {@link #REPORTING_NARROWED_APIS} names.
     */
    static ArchRule reportingHearsOnlyTheEventsOfItsSources(String root) {
        return readsOnlyTheEventsOf(root, REPORTING, REPORTING_SOURCES, REPORTING_NARROWED_APIS);
    }

    /**
     * Rule 8, with the reader, its sources and its narrowed APIs given: no class of the reader
     * module, its adapters included, uses a class of a source module other than one in, or below,
     * that module's {@code events}, nor a class in, or below, a narrowed module's {@code api} that
     * the map doesn't name. Rules 1 and 2 allow a source's {@code api}; this rule is narrower,
     * because the reader reads its sources through the views they publish, which no Java dependency
     * shows.
     *
     * @param reader the module bound by the rule, such as {@code reporting}
     * @param sources the modules whose events alone it may use
     * @param narrowedApis each module whose {@code api} the reader may use only in part, and the
     *     types it may use, by their names below the root
     */
    static ArchRule readsOnlyTheEventsOf(
            String root, String reader, Set<String> sources, Map<String, Set<String>> narrowedApis) {
        return classes()
                .that()
                .resideInAPackage(root + "." + reader + "..")
                .should(dependOnlyOn(
                        "the events of " + new TreeSet<>(sources) + " among those modules' classes, and of the api"
                                + " of " + new TreeSet<>(narrowedApis.keySet()) + " only the types named for each",
                        (item, target) -> isNoSourceOrItsEvents(root, sources, target)
                                && isOutsideOrNamedInANarrowedApi(root, narrowedApis, target)))
                .because("ADR-120 P5: Reporting reads the other modules only through the views they publish,"
                        + " and hears their events; it calls no other module's Java API, and takes only the"
                        + " Actor from Access");
    }

    /**
     * The modules of a graph that reach themselves through its arrows, in name order; empty when
     * the graph is acyclic, which rule 7's Javadoc relies on. An arrow to a module the graph does
     * not hold leads nowhere.
     *
     * @param graph each module, and the modules it may depend on
     */
    static SortedSet<String> modulesOnACycle(Map<String, Set<String>> graph) {
        return graph.keySet().stream()
                .filter(module -> reachesItself(graph, module))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * The names of the packages {@link #packagesNotNullMarked} checks: every application package
     * under a root, the shared application package included, that holds a class.
     */
    static SortedSet<String> nullMarkedSubjects(String root, JavaClasses classes) {
        return applicationPackages(root, classes)
                .map(JavaPackage::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Rule 5: the names of the application packages under a root, the shared application package
     * included, whose {@code package-info} is missing or carries no {@code @NullMarked}.
     */
    static SortedSet<String> packagesNotNullMarked(String root, JavaClasses classes) {
        return applicationPackages(root, classes)
                .filter(javaPackage -> !javaPackage.isAnnotatedWith(NullMarked.class))
                .map(JavaPackage::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * The names of the packages {@link #adapterPackagesNotNullMarked} checks: every adapter package
     * under a root, the kernel's included, that holds a class. A package declared ahead holds its
     * {@code package-info}, which compiles to a class, so it is among them (ADR-130 decision 1).
     */
    static SortedSet<String> nullMarkedAdapterSubjects(String root, JavaClasses classes) {
        return adapterPackages(root, classes).map(JavaPackage::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Rule 5, for the adapters: the names of the adapter packages under a root, the kernel's
     * included, whose {@code package-info} is missing or carries no {@code @NullMarked} (ADR-130
     * decision 7).
     */
    static SortedSet<String> adapterPackagesNotNullMarked(String root, JavaClasses classes) {
        return adapterPackages(root, classes)
                .filter(javaPackage -> !javaPackage.isAnnotatedWith(NullMarked.class))
                .map(JavaPackage::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Rule 6: no class in the kernel's own package, in a domain or in an events package depends on
     * the shared application package, so no domain can reach the {@code Clock} port.
     */
    static ArchRule domainReachesNoApplicationPort(String root) {
        String sharedApplication = root + "." + KERNEL + "." + APPLICATION;
        return classes()
                .that()
                .resideInAnyPackage(root + "." + KERNEL, root + ".*." + DOMAIN + "..", root + ".*." + EVENTS + "..")
                .should(dependOnlyOn(
                        "nothing in " + sharedApplication,
                        (item, target) -> !isIn(target.getPackageName(), sharedApplication)))
                .because("DL §3.9 rule 2 and ADR-120: now, today and new IDs are parameters of the domain; only"
                        + " the application reads them from the shared ports");
    }

    /**
     * One slice per package under {@code <root>.<module>.application}, the shared application
     * package included; every other class is left out, as {@link DomainPackageRules#domainPackages}
     * does for the domain.
     */
    static SliceAssignment applicationPackageSlices(String root) {
        PackageMatcher application = PackageMatcher.of(applicationLayers(root));
        return new SliceAssignment() {
            @Override
            public SliceIdentifier getIdentifierOf(JavaClass type) {
                return application.matches(type.getPackageName())
                        ? SliceIdentifier.of(type.getPackageName())
                        : SliceIdentifier.ignore();
            }

            @Override
            public String getDescription() {
                return "the packages under " + root + ".<module>." + APPLICATION;
            }
        };
    }

    /**
     * Rule 7, for cycles: no cycle between application packages. Each package is a slice of its
     * own, so a cycle across modules would show too, which rule 2's graph already forbids, because
     * {@link #modulesOnACycle} finds no cycle in it.
     */
    static ArchRule applicationPackagesAreFreeOfCycles(String root) {
        return slices().assignedFrom(applicationPackageSlices(root))
                .should()
                .beFreeOfCycles()
                .because("ADR-118 rule 7, carried to the application: the use-case and port packages of"
                        + " each aggregate use the roots and each other one way only");
    }

    /**
     * Rule 7, for each package: no class in a module's application uses a class in a package under
     * its own. This covers the {@code service} and {@code port.out} roots, and an aggregate's package
     * that has a concept package beneath it, such as {@code service.plan} over {@code
     * service.plan.edit} (ADR-125). The cycle rule can't see this when the subpackage never names the
     * package above it, and rule 1 lets any class reach its own module's application.
     */
    static ArchRule applicationPackagesUseNoSubpackage(String root) {
        return classes()
                .that()
                .resideInAPackage(applicationLayers(root))
                .should(new ArchCondition<>("use no class in a package under its own") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String below = item.getPackageName() + ".";
                        item.getDirectDependenciesFromSelf().stream()
                                .filter(dependency -> baseType(dependency.getTargetClass())
                                        .getPackageName()
                                        .startsWith(below))
                                .forEach(dependency ->
                                        events.add(SimpleConditionEvent.violated(item, dependency.getDescription())));
                    }
                })
                .because("ADR-118 rule 2, carried to the application by ADR-125: a package holds what the"
                        + " packages beneath it share, and imports none of them");
    }

    /**
     * Rule 9: within a module, adapters stay apart. A class in one adapter, {@code adapter.in.<a>}
     * or {@code adapter.out.<a>} with its subpackages, uses no class of another adapter of its own
     * top-level package, so {@code in} never uses {@code out}, nor {@code out} {@code in}, nor one
     * adapter another of its kind (TS §2.1, ADR-130 decision 7). A class directly in {@code
     * adapter.in} or {@code adapter.out} counts as an adapter of its own. The adapters beside the
     * kernel share its top-level package, so they stay apart from each other too.
     */
    static ArchRule adaptersOfAModuleStayApart(String root) {
        return classes()
                .that()
                .resideInAPackage(adapterLayers(root))
                .should(dependOnlyOn(
                        "no other adapter of its own module",
                        (item, target) -> adapterOf(root, target.getPackageName())
                                .filter(theirs -> topLevelPackage(root, target).equals(topLevelPackage(root, item)))
                                .map(theirs -> theirs.equals(
                                        adapterOf(root, item.getPackageName()).orElse("")))
                                .orElse(true)))
                .because("TS §2.1 and ADR-130 decision 7: an adapter reaches another only through a port");
    }

    /**
     * Rule 10: no adapter uses another module's adapter. The kernel and {@code web} are no modules
     * ({@link #NOT_MODULES}), so this rule leaves the adapters they hold, such as {@code
     * shared.adapter.out.persistence}, usable. Rule 2 still decides whether a module may reach
     * {@code web} at all, and refuses it for good: nothing depends on {@code web} (ADR-140).
     */
    static ArchRule adaptersUseNoOtherModulesAdapter(String root) {
        return classes()
                .that()
                .resideInAPackage(adapterLayers(root))
                .should(dependOnlyOn(
                        "no adapter of another module",
                        (item, target) ->
                                adapterOf(root, target.getPackageName()).isEmpty()
                                        || topLevelPackage(root, target)
                                                .filter(NOT_MODULES::contains)
                                                .isPresent()
                                        || topLevelPackage(root, target).equals(topLevelPackage(root, item))))
                .because("TS §2.1 and ADR-130 decision 7: modules meet through their api, spi and events,"
                        + " never through each other's adapters");
    }

    /**
     * Rule 11: the adapters beside the kernel use nothing outside it. A class in, or below, {@code
     * shared.adapter} depends on no class of another top-level package. Rule 2 skips the kernel's
     * classes and rules 9 and 10 see only adapters, so without this rule one module could reach
     * another through a shared adapter, along an arrow DL §2.1 lacks (ADR-130 decision 7). {@code
     * web} is refused too: nothing depends on it (ADR-140), and rule 2 skips it, so a shared adapter
     * that used it could reach the modules through it (ADR-131).
     */
    static ArchRule sharedAdaptersUseNoModule(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "." + KERNEL + "." + ADAPTER + "..")
                .should(dependOnlyOn(
                        "nothing outside the kernel",
                        (item, target) -> topLevelPackage(root, target)
                                .map(KERNEL::equals)
                                .orElse(true)))
                .because("DL §2.1, ADR-130 decision 7 and ADR-131: every module depends on the kernel and never"
                        + " the reverse, so a shared adapter that used a module, or web, would open an arrow the"
                        + " graph lacks");
    }

    /**
     * Rule 12: the generated optimizer client stays in the optimizer adapters. A class outside
     * {@link #OPTIMIZER_CLIENT} that uses it lies in, or below, the {@code adapter.out.optimizer}
     * of one of {@link #SOLVING_MODULES}, so the client's types stop at those adapters and no
     * other module calls the optimizer (ADR-043, ADR-130 decision 5, ADR-131).
     */
    static ArchRule optimizerClientStaysInItsAdapters(String root) {
        return generatedClientStaysIn(root, OPTIMIZER_CLIENT, SOLVING_MODULES);
    }

    /**
     * Rule 12, for any client package below the root, such as a fixture's, and the modules whose
     * {@code adapter.out.optimizer} may use it.
     */
    static ArchRule generatedClientStaysIn(String root, String client, Set<String> modules) {
        String clientPackage = root + "." + client;
        return classes()
                .that()
                .resideOutsideOfPackage(clientPackage + "..")
                .should(dependOnlyOn(
                        "the optimizer client from an optimizer adapter only",
                        (item, target) -> !isIn(target.getPackageName(), clientPackage)
                                || modules.stream()
                                        .anyMatch(module -> isIn(
                                                item.getPackageName(),
                                                root + "." + module + "." + ADAPTER + "." + OUT + ".optimizer"))))
                .because("ADR-043, ADR-130 decision 5 and ADR-131: generated types stop at the adapter that wraps"
                        + " them, and only the modules DL §2.1 draws to the optimizer call it");
    }

    /**
     * Rule 13: {@code web} uses the modules only through what they publish. A class in, or below,
     * {@code web} uses, among the classes of the modules, only those in, or below, a module's
     * {@code application.api} or {@code events}. The kernel, {@code web} itself, the root's own
     * package and anything outside the root are open to it. Rule 2 skips {@code web}, which is no
     * module, and refuses every module a dependency on it, so {@code web} sits above the modules;
     * this rule keeps it from reaching into one (ADR-140).
     */
    static ArchRule webUsesOnlyTheModulesApiAndEvents(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "." + WEB + "..")
                .should(dependOnlyOn(
                        "the api and events of the modules among their classes",
                        (item, target) -> isNoModuleOrItsApiOrEvents(root, target)))
                .because("ADR-140: web reaches the modules only through their application.api and events");
    }

    /**
     * Rule 14, for production's holders, {@link #CALLS_HOLDERS}. Its subject is every class outside
     * {@code web} and the modules' {@code adapter.in.web}, which is never empty, so {@code
     * archRule.failOnEmptyShould} can't fail it; {@link #missingHolders} checks the holders exist.
     */
    static ArchRule onlyTheWebReadsTheCallsHolders(String root) {
        return onlyTheWebReadsTheCallsHolders(root, CALLS_HOLDERS);
    }

    /**
     * Rule 14, with the holders given, so the fixtures can bring their own: a class that depends on
     * a holder lies in, or below, {@code web} or a module's {@code adapter.in.web}, or is that
     * holder, whose use of itself ArchUnit reports as no dependency. The kernel's {@code
     * adapter.in.web} is no module's, so it may not. A use case takes its {@code Actor} as a
     * parameter, and a listener, a job or a CLI step has no web call to read (ADR-140 decision 4).
     *
     * @param holders the holders, by their names below the root, such as {@code
     *     access.application.api.CurrentActor}
     */
    static ArchRule onlyTheWebReadsTheCallsHolders(String root, Set<String> holders) {
        Set<String> holderNames =
                holders.stream().map(holder -> root + "." + holder).collect(Collectors.toUnmodifiableSet());
        return classes()
                .that(DescribedPredicate.describe(
                        "lie outside web and the modules' adapter.in.web", type -> !mayReadTheCallsHolders(root, type)))
                .should(dependOnlyOn(
                        "no holder of the call's caller but itself " + new TreeSet<>(holders),
                        (item, target) -> !holderNames.contains(target.getName())))
                .because("ADR-140 decision 4: only web and the modules' controllers read the call's Actor and"
                        + " CallContext; a use case takes its Actor as a parameter");
    }

    /**
     * Rule 15: a module's root package holds its {@code package-info} and its {@code
     * <Module>Configuration} alone, and that configuration only lists its own module's use cases
     * (ADR-140 decision 10, ADR-119 decision 4). A class directly in a module's root, a top-level
     * package outside {@link #NOT_MODULES}, is a {@code package-info} or a class whose name ends in
     * {@code Configuration}; such a class isn't public, declares no {@code @Bean} method and no
     * {@code @ComponentScan}, and every class its {@code @Import} names lies in, or below, its own
     * module's {@code application.service}. So no constructor is written twice, and no throwing body
     * is wired by a scan.
     *
     * <p>A {@code @Import} that arrives through another annotation, an {@code @ImportResource} and a
     * {@code @ComponentScans} are refused too, since each wires beans that the list doesn't show.
     *
     * <p>Its subject is every class in or below a top-level package, which is never empty, so {@code
     * archRule.failOnEmptyShould} can't fail it, as rule 14's can't; only those directly in a
     * module's root are checked. {@link #onlyTheModulesConfigurationsWireTheirUseCases} is the
     * rule's other half.
     */
    static ArchRule moduleRootsHoldOnlyTheirConfiguration(String root) {
        return classes()
                .that()
                .resideInAPackage(root + ".*..")
                .should(
                        new ArchCondition<>("be, directly in a module's root, its package-info or its package-private"
                                + " configuration, which declares no @Bean or @ComponentScan and imports only its own"
                                + " module's services") {
                            @Override
                            public void check(JavaClass item, ConditionEvents events) {
                                Optional<String> module =
                                        topLevelPackage(root, item).filter(name -> !NOT_MODULES.contains(name));
                                if (module.isEmpty()
                                        || !item.getPackageName().equals(root + "." + module.get())
                                        || item.getSimpleName().equals(PACKAGE_INFO)) {
                                    return;
                                }
                                if (!item.getSimpleName().endsWith(CONFIGURATION_SUFFIX)) {
                                    events.add(
                                            SimpleConditionEvent.violated(
                                                    item,
                                                    item.getName() + " is in module " + module.get()
                                                            + "'s root, which holds only its package-info and its configuration"));
                                    return;
                                }
                                String services = root + "." + module.get() + "." + APPLICATION + "." + SERVICE;
                                configurationViolations(item, services)
                                        .forEach(violation ->
                                                events.add(SimpleConditionEvent.violated(item, violation)));
                            }
                        })
                .because("ADR-140 decision 10: each module's use cases become beans only through the @Import of"
                        + " its package-private configuration, in the pull request that builds each body");
    }

    /**
     * Rule 15, for every other class: outside a module's root configuration, no class's {@code
     * @Import}, direct or through another annotation, names a class in, or below, a module's {@code
     * application.service}, and no {@code @Bean} method returns or constructs one. With the first
     * half, a module's root configuration is the one class whose {@code @Import} list may name a use
     * case, and no {@code @Bean} method anywhere makes one (ADR-140 decision 10, ADR-142). It reads
     * only those two. A use case carries no Spring annotation but {@code @Transactional} (rule 1),
     * so a plain component scan registers none; one registered any other way, such as through a
     * scan's include filter or {@code registerBean}, is left to review.
     *
     * <p>Its subject is every class under the root, the root's own package included, which is never
     * empty.
     */
    static ArchRule onlyTheModulesConfigurationsWireTheirUseCases(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(
                        new ArchCondition<>("import no module's service and make none in a @Bean method, unless it is"
                                + " that module's root configuration") {
                            @Override
                            public void check(JavaClass item, ConditionEvents events) {
                                if (isAModulesRootConfiguration(root, item)) {
                                    return;
                                }
                                importsOf(item)
                                        .filter(imported -> isAService(root, imported))
                                        .forEach(imported -> events.add(SimpleConditionEvent.violated(
                                                item, item.getName() + " imports the use case " + imported.getName())));
                                item.getMethods().stream()
                                        .filter(method ->
                                                method.isAnnotatedWith(BEAN) || method.isMetaAnnotatedWith(BEAN))
                                        .filter(method -> makesAService(root, method))
                                        .forEach(method -> events.add(SimpleConditionEvent.violated(
                                                item,
                                                method.getFullName() + " is a @Bean method that makes a use case")));
                            }
                        })
                .because("ADR-140 decision 10: each module's use cases become beans only through the @Import of"
                        + " its package-private configuration");
    }

    /**
     * Rule 16: no handler takes a streaming parameter. No method of a class annotated, directly or
     * through another annotation, {@code @Controller} (as {@code @RestController} is), and no
     * method annotated {@code @RequestMapping} or one of its variants, declares a parameter of one
     * of {@link #STREAMING_TYPES} or a subtype. The call wrapper re-invokes a handler with the
     * arguments it already read (ADR-140 decision 6), and the body's size limit covers only what a
     * converter reads, so a handler that read its own stream would read an empty one on a retry and
     * an unlimited one on the first try (ADR-142).
     *
     * <p>The annotations are read by name, as rule 15's are. Its subject is every class under the
     * root, which is never empty, so {@code archRule.failOnEmptyShould} can't fail it before the
     * modules have controllers; only their handler methods are checked.
     */
    static ArchRule noHandlerTakesAStreamingParameter(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<>("declare no handler method that takes one of " + STREAMING_TYPES) {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        boolean controller = item.isAnnotatedWith(CONTROLLER) || item.isMetaAnnotatedWith(CONTROLLER);
                        item.getMethods().stream()
                                .filter(method -> controller
                                        || method.isAnnotatedWith(REQUEST_MAPPING)
                                        || method.isMetaAnnotatedWith(REQUEST_MAPPING))
                                .forEach(method -> method.getRawParameterTypes().stream()
                                        .filter(ApplicationRules::isStreaming)
                                        .forEach(parameter -> events.add(SimpleConditionEvent.violated(
                                                item,
                                                method.getFullName() + " is a handler that takes a "
                                                        + parameter.getName()))));
                    }
                })
                .because("ADR-140 decision 6 and ADR-142: the call wrapper retries a handler with the arguments"
                        + " Spring already read, and only a converter's read is capped");
    }

    /**
     * Rule 17, for production's {@link #ACTOR}: only {@code web.security} builds an {@code Actor}
     * (ADR-140 decision 4).
     */
    static ArchRule onlyWebSecurityBuildsAnActor(String root) {
        return onlyWebSecurityBuilds(root, ACTOR);
    }

    /**
     * Rule 17, with the actor's type given, so the fixtures can bring their own: no class outside
     * {@code web.security}, and below it, calls the actor's constructor or refers to it, as {@code
     * Actor::new} does. The actor comes from the request's principal, which only {@code
     * web.security} proves: from the stand-in session now, and from the access token after sign-in.
     * A use case takes its {@code Actor} as a parameter, and a controller reads {@code CurrentActor}.
     * The rule reads production classes alone, so a test builds one through {@code TestActors}.
     *
     * @param actor the actor's type, by its name below the root, such as {@code
     *     access.application.api.Actor}
     */
    static ArchRule onlyWebSecurityBuilds(String root, String actor) {
        String actorName = root + "." + actor;
        String security = root + "." + WEB + "." + SECURITY;
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<>("build no " + actor + " outside web.security") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (isIn(item.getPackageName(), security)) {
                            return;
                        }
                        Stream.concat(
                                        item.getConstructorCallsFromSelf().stream(),
                                        item.getConstructorReferencesFromSelf().stream())
                                .filter(access ->
                                        access.getTargetOwner().getName().equals(actorName))
                                .forEach(access ->
                                        events.add(SimpleConditionEvent.violated(item, access.getDescription())));
                    }
                })
                .because("ADR-140 decision 4: only web.security proves who the caller is, so only it builds the"
                        + " Actor a use case takes");
    }

    /**
     * Rule 18, for production's holders and {@link #CALL_WRAPPER}: only {@code web}'s call wrapper
     * binds the call's holders (ADR-140 decision 4).
     */
    static ArchRule onlyTheCallWrapperBindsTheHolders(String root) {
        return onlyTheCallWrapperBindsTheHolders(root, CALLS_HOLDERS, CALL_WRAPPER);
    }

    /**
     * Rule 18, with the holders and the wrapper given, so the fixtures can bring their own: no class
     * but the wrapper and its nested classes calls, or refers to, a holder's method other than
     * {@code get()} with no parameter, which reads. Every other method binds, so a binding method
     * added under a new name, or as an overload of {@code get}, is caught too. A holder's own use of its methods is no dependency. Rule 14 already keeps the
     * holders to {@code web} and the controllers; this keeps a controller, or another class of
     * {@code web}, from binding a caller of its own choosing.
     *
     * @param holders the holders, by their names below the root
     * @param wrapper the wrapper, by its name below the root, such as {@code web.CallWrapper}
     */
    static ArchRule onlyTheCallWrapperBindsTheHolders(String root, Set<String> holders, String wrapper) {
        Set<String> holderNames =
                holders.stream().map(holder -> root + "." + holder).collect(Collectors.toUnmodifiableSet());
        String wrapperName = root + "." + wrapper;
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(
                        new ArchCondition<>("bind no holder of the call's caller " + new TreeSet<>(holders)
                                + " unless it is " + wrapper) {
                            @Override
                            public void check(JavaClass item, ConditionEvents events) {
                                if (isOrNests(item, wrapperName)
                                        || holderNames.stream().anyMatch(holder -> isOrNests(item, holder))) {
                                    return;
                                }
                                Stream.concat(
                                                item.getMethodCallsFromSelf().stream(),
                                                item.getMethodReferencesFromSelf().stream())
                                        .filter(access -> holderNames.contains(
                                                access.getTargetOwner().getName()))
                                        .filter(access -> !isTheHoldersRead(access))
                                        .forEach(access -> events.add(
                                                SimpleConditionEvent.violated(item, access.getDescription())));
                            }
                        })
                .because("ADR-140 decision 4: the call wrapper binds the holders from the principal that"
                        + " web.security set, so no other class picks the caller a use case sees");
    }

    /**
     * Rule 19: the root package depends on nothing below it. No class directly in the root, such as
     * {@code DispatchFlowApplication} or {@code StandInProfiles}, uses a class of a module, of the
     * kernel or of {@code web}. Every adapter and {@code web} may use the root's {@code @StandInOnly}
     * (ADR-147), which rules 2, 10, 11 and 13 leave open, so a root that used them back would close a
     * cycle that no other rule sees. A module's wiring lives in its own {@code <Module>Configuration}
     * (rule 15), never in the root.
     */
    static ArchRule theRootDependsOnNothingBelowIt(String root) {
        return classes()
                .that()
                .resideInAPackage(root)
                .should(dependOnlyOn(
                        "nothing below the root",
                        (item, target) -> !isIn(target.getPackageName(), root)
                                || target.getPackageName().equals(root)))
                .because("ADR-147: the stand-ins of every module use the root's condition, so the root uses none"
                        + " of them, or the dependency graph would hold a cycle");
    }

    /**
     * Rule 20: {@code web.security} uses no class directly in {@code web}. The call wrapper and the
     * race's audit in {@code web} read the caller that {@code web.security} builds, so a use the other
     * way would close a cycle between the two, which rule 13, reading {@code web} as one package,
     * can't see. What both need, such as their filters' order, sits in {@code shared.adapter.in.web}
     * (ADR-147).
     */
    static ArchRule webSecurityUsesNothingDirectlyInWeb(String root) {
        String web = root + "." + WEB;
        return classes()
                .that()
                .resideInAPackage(web + "." + SECURITY + "..")
                .should(dependOnlyOn(
                        "no class directly in web",
                        (item, target) -> !target.getPackageName().equals(web)))
                .because("ADR-147: web depends on web.security, never the reverse");
    }

    /**
     * Rule 21, for production's {@code Actor} and {@code CallContext}: no handler takes either as a
     * parameter (ADR-140 decision 4, ADR-147).
     */
    static ArchRule noHandlerTakesTheCallersTypes(String root) {
        return noHandlerTakes(root, Set.of(ACTOR, CALL_CONTEXT));
    }

    /**
     * Rule 21, with the types given, so the fixtures can bring their own: no handler method, a
     * method of a {@code @Controller} or one annotated {@code @RequestMapping} or a variant, as rule
     * 16 reads them, declares a parameter of one of the types. Spring MVC would build such a
     * parameter from the request's own parameters, by its constructor, which rule 17 can't see, so a
     * client would choose its caller. A controller reads {@code CurrentActor} or {@code CurrentCall}
     * instead.
     *
     * @param types the types, by their names below the root
     */
    static ArchRule noHandlerTakes(String root, Set<String> types) {
        Set<String> typeNames = types.stream().map(type -> root + "." + type).collect(Collectors.toUnmodifiableSet());
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<>("declare no handler method that takes one of " + new TreeSet<>(types)) {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        boolean controller = item.isAnnotatedWith(CONTROLLER) || item.isMetaAnnotatedWith(CONTROLLER);
                        item.getMethods().stream()
                                .filter(method -> controller
                                        || method.isAnnotatedWith(REQUEST_MAPPING)
                                        || method.isMetaAnnotatedWith(REQUEST_MAPPING))
                                .forEach(method -> method.getRawParameterTypes().stream()
                                        .filter(parameter -> typeNames.contains(parameter.getName()))
                                        .forEach(parameter -> events.add(SimpleConditionEvent.violated(
                                                item,
                                                method.getFullName() + " is a handler that takes a "
                                                        + parameter.getName()))));
                    }
                })
                .because("ADR-140 decision 4: the caller comes from the principal web.security proved, never from"
                        + " the request's parameters");
    }

    /**
     * The names of the holders given that are no class among those given, so a renamed holder can't
     * leave rules 14 and 18 matching nothing.
     *
     * @param holders the holders, by their names below the root
     * @return the names that are no class, sorted; empty when every holder exists
     */
    static SortedSet<String> missingHolders(String root, JavaClasses classes, Set<String> holders) {
        return holders.stream()
                .filter(holder -> !classes.contain(root + "." + holder))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Whether an access to a holder is its one read: {@code get()}, with no parameter. */
    private static boolean isTheHoldersRead(JavaCodeUnitAccess<?> access) {
        return access.getName().equals(HOLDERS_READ)
                && access.getTarget().getRawParameterTypes().isEmpty();
    }

    /** Whether a class is the named one, or nested in it. */
    private static boolean isOrNests(JavaClass item, String name) {
        return item.getName().equals(name) || item.getName().startsWith(name + "$");
    }

    /** What a module's configuration does that rule 15 forbids, one message each. */
    private static Stream<String> configurationViolations(JavaClass configuration, String services) {
        Stream<String> isPublic = configuration.getModifiers().contains(JavaModifier.PUBLIC)
                ? Stream.of(configuration.getName() + " is public")
                : Stream.empty();
        Stream<String> beans = configuration.getMethods().stream()
                .filter(method -> method.isAnnotatedWith(BEAN) || method.isMetaAnnotatedWith(BEAN))
                .map(method -> method.getFullName() + " is a @Bean method");
        Stream<String> scans = isAnnotatedOrMetaAnnotatedWith(configuration, COMPONENT_SCAN)
                        || isAnnotatedOrMetaAnnotatedWith(configuration, COMPONENT_SCANS)
                ? Stream.of(configuration.getName() + " scans for components")
                : Stream.empty();
        Stream<String> resources = isAnnotatedOrMetaAnnotatedWith(configuration, IMPORT_RESOURCE)
                ? Stream.of(configuration.getName() + " imports a resource")
                : Stream.empty();
        Stream<String> metaImports = configuration.getAnnotations().stream()
                .map(JavaAnnotation::getRawType)
                .filter(annotation ->
                        !annotation.getName().equals(IMPORT) && isAnnotatedOrMetaAnnotatedWith(annotation, IMPORT))
                .map(annotation ->
                        configuration.getName() + " imports through @" + annotation.getSimpleName() + ", not its list");
        Stream<String> imports = configuration
                .tryGetAnnotationOfType(IMPORT)
                .flatMap(annotation -> annotation.get(IMPORTED))
                .map(imported -> Arrays.stream((JavaClass[]) imported))
                .orElseGet(Stream::empty)
                .filter(imported -> !isIn(imported.getPackageName(), services))
                .map(imported -> configuration.getName() + " imports " + imported.getName() + ", outside " + services);
        return Stream.of(isPublic, beans, scans, resources, metaImports, imports)
                .flatMap(Function.identity());
    }

    private static boolean isAnnotatedOrMetaAnnotatedWith(JavaClass type, String annotation) {
        return type.isAnnotatedWith(annotation) || type.isMetaAnnotatedWith(annotation);
    }

    /** A class directly in a module's root whose name ends in {@code Configuration}. */
    private static boolean isAModulesRootConfiguration(String root, JavaClass type) {
        return topLevelPackage(root, type)
                        .filter(module -> !NOT_MODULES.contains(module))
                        .filter(module -> type.getPackageName().equals(root + "." + module))
                        .isPresent()
                && type.getSimpleName().endsWith(CONFIGURATION_SUFFIX);
    }

    /**
     * Every class an {@code @Import} names on a type, directly or through its annotations and
     * theirs, each annotation type read once.
     */
    private static Stream<JavaClass> importsOf(JavaClass type) {
        List<JavaClass> imported = new ArrayList<>();
        collectImports(type.getAnnotations(), new HashSet<>(), imported);
        return imported.stream();
    }

    private static void collectImports(
            Set<? extends JavaAnnotation<?>> annotations, Set<String> read, List<JavaClass> imported) {
        for (JavaAnnotation<?> annotation : annotations) {
            JavaClass type = annotation.getRawType();
            if (type.getName().equals(IMPORT)) {
                annotation.get(IMPORTED).ifPresent(value -> imported.addAll(Arrays.asList((JavaClass[]) value)));
            } else if (read.add(type.getName())) {
                collectImports(type.getAnnotations(), read, imported);
            }
        }
    }

    /** Whether a class lies in, or below, a module's {@code application.service}. */
    private static boolean isAService(String root, JavaClass type) {
        String[] segments = segmentsBelow(root, type.getPackageName());
        return segments.length >= 3 && segments[1].equals(APPLICATION) && segments[2].equals(SERVICE);
    }

    /** Whether a {@code @Bean} method returns a service's type or calls a service's constructor. */
    private static boolean makesAService(String root, JavaMethod method) {
        return isAService(root, method.getRawReturnType())
                || method.getConstructorCallsFromSelf().stream()
                        .anyMatch(call -> isAService(root, call.getTargetOwner()));
    }

    private static boolean isStreaming(JavaClass type) {
        return STREAMING_TYPES.stream().anyMatch(type::isAssignableTo);
    }

    /**
     * The adapters under a root that hold a class, by top-level package, each named below its
     * {@code adapter} package as {@link #DECLARED_ADAPTERS} names them. A class directly in {@code
     * adapter.in} or {@code adapter.out} shows as {@code in} or {@code out}, and one directly in
     * {@code adapter} as an empty name.
     */
    static Map<String, Set<String>> adaptersByTopLevelPackage(String root, JavaClasses classes) {
        return adapterPackages(root, classes)
                .map(JavaPackage::getName)
                .flatMap(name -> adapterOf(root, name).stream())
                .collect(Collectors.groupingBy(
                        ApplicationRules::topLevelOfAnAdapter,
                        Collectors.mapping(ApplicationRules::nameBelowItsAdapterPackage, Collectors.toSet())));
    }

    /** The top-level package of an adapter that {@link #adapterOf} names, such as {@code access}. */
    private static String topLevelOfAnAdapter(String adapter) {
        return adapter.substring(0, adapter.indexOf('.'));
    }

    /**
     * An adapter's name below its top-level package's {@code adapter} package, such as {@code
     * in.web}; empty for a class directly in that package.
     */
    private static String nameBelowItsAdapterPackage(String adapter) {
        String adapterPackage = topLevelOfAnAdapter(adapter) + "." + ADAPTER;
        return adapter.equals(adapterPackage) ? "" : adapter.substring(adapterPackage.length() + 1);
    }

    /** Each module's application layer, with the shared application package. */
    private static String applicationLayers(String root) {
        return root + ".*." + APPLICATION + "..";
    }

    /** Each module's adapters, with the kernel's. */
    private static String adapterLayers(String root) {
        return root + ".*." + ADAPTER + "..";
    }

    private static Stream<JavaPackage> applicationPackages(String root, JavaClasses classes) {
        return classes.that(JavaClass.Predicates.resideInAPackage(applicationLayers(root))).stream()
                .map(JavaClass::getPackage);
    }

    private static Stream<JavaPackage> adapterPackages(String root, JavaClasses classes) {
        return classes.that(JavaClass.Predicates.resideInAPackage(adapterLayers(root))).stream()
                .map(JavaClass::getPackage);
    }

    /**
     * The adapter a package belongs to below the root: {@code <module>.adapter.<in|out>.<name>}, or
     * {@code <module>.adapter.<in|out>} for a class directly under its direction. Any other package
     * under {@code adapter}, which ADR-130 declares none of, is an adapter of its own, {@code
     * <module>.adapter.<x>}, or {@code <module>.adapter} for a class directly in it, so rules 9 and
     * 10 see it on both sides of a dependency. Empty outside every adapter.
     */
    private static Optional<String> adapterOf(String root, String packageName) {
        String[] segments = segmentsBelow(root, packageName);
        if (segments.length < 2 || !segments[1].equals(ADAPTER)) {
            return Optional.empty();
        }
        boolean underADirection = segments.length > 2 && (segments[2].equals(IN) || segments[2].equals(OUT));
        int length = underADirection ? ADAPTER_SEGMENTS : ADAPTER_SEGMENTS - 1;
        return Optional.of(String.join(".", Arrays.copyOf(segments, Math.min(segments.length, length))));
    }

    /**
     * What a class of {@code <root>.<module>.application} may depend on. Another module's {@code
     * api} and {@code events} are allowed here whatever the module, and rule 2 decides which
     * modules. Another module's {@code spi} is allowed only as {@link #isPublishedToTheModule}
     * says.
     */
    private static boolean isAllowedInTheApplication(
            String root, String moduleAnnotations, Map<String, String> implementers, JavaClass item, JavaClass target) {
        String targetPackage = target.getPackageName();
        if (isInAny(targetPackage, ALWAYS_ALLOWED)
                || (isIn(targetPackage, root + "." + KERNEL)
                        && !isIn(targetPackage, root + "." + KERNEL + "." + ADAPTER))
                || TRANSACTIONAL.contains(target.getName())
                || (item.getSimpleName().equals(PACKAGE_INFO) && isIn(targetPackage, moduleAnnotations))) {
            return true;
        }
        String itemModule = topLevelPackage(root, item).orElseThrow();
        String module = root + "." + itemModule;
        if (isIn(targetPackage, module + "." + DOMAIN)
                || isIn(targetPackage, module + "." + EVENTS)
                || isIn(targetPackage, module + "." + APPLICATION)) {
            return true;
        }
        return isPublishedToTheModule(root, target, itemModule, implementers);
    }

    /**
     * A module's {@code application.api} or {@code events}, or below one; or a class of its {@code
     * application.spi}, or below it, published to {@code itemModule} by a dashed arrow. An
     * interface an arrow names is published only to the module that arrow names, so an implementer
     * of one interface can't call another module's. Every other class there, such as a record
     * beside the interfaces, is published to each module an arrow names for one of that spi's
     * interfaces, because an implementation uses the records its interface takes and returns.
     */
    private static boolean isPublishedToTheModule(
            String root, JavaClass target, String itemModule, Map<String, String> implementers) {
        String[] segments = segmentsBelow(root, target.getPackageName());
        if (isInAnApiOrEvents(segments)) {
            return true;
        }
        if (segments.length < 3 || !segments[1].equals(APPLICATION) || !segments[2].equals(SPI)) {
            return false;
        }
        Optional<String> arrowsImplementer = target.isInterface()
                ? Optional.ofNullable(implementers.get(nameBelow(root, target)))
                : Optional.empty();
        return arrowsImplementer
                .map(itemModule::equals)
                .orElseGet(() -> implementsAnSpiOf(segments[0], itemModule, implementers));
    }

    /**
     * Whether a dashed arrow names {@code module} to implement an interface of {@code owner}'s spi.
     */
    private static boolean implementsAnSpiOf(String owner, String module, Map<String, String> implementers) {
        String ownersSpi = owner + "." + APPLICATION + "." + SPI + ".";
        return implementers.entrySet().stream()
                .anyMatch(arrow ->
                        arrow.getKey().startsWith(ownersSpi) && arrow.getValue().equals(module));
    }

    /**
     * Exactly one concrete implementation of each interface that is no fact's variants, named
     * {@code expectedName} and under the {@code application.service} of the module {@code
     * expectedModule} names for it; an interface it names no module for fails.
     */
    private static ArchCondition<JavaClass> haveOneImplementation(
            String root,
            String where,
            Function<JavaClass, Optional<String>> expectedModule,
            Function<JavaClass, String> expectedName) {
        return new ArchCondition<>(
                "have exactly one concrete implementation, named for the interface, under the application.service of "
                        + where + ", when an interface that is no fact's variants") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                // A package-info compiles to a synthetic interface, which no class implements.
                if (!item.isInterface()
                        || item.isAnnotation()
                        || modelsAFactsVariants(item)
                        || item.getSimpleName().equals(PACKAGE_INFO)) {
                    return;
                }
                Optional<String> expected = expectedModule.apply(item);
                if (expected.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName()
                                    + " names no module to implement it, so none is under the application.service of "
                                    + where));
                    return;
                }
                List<JavaClass> implementations = item.getAllSubclasses().stream()
                        .filter(type ->
                                !type.isInterface() && !type.getModifiers().contains(JavaModifier.ABSTRACT))
                        .sorted((first, second) -> first.getName().compareTo(second.getName()))
                        .toList();
                if (implementations.size() != 1) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " has " + implementations.size() + " implementations, not one: "
                                    + implementations.stream()
                                            .map(JavaClass::getName)
                                            .toList()));
                    return;
                }
                JavaClass implementation = implementations.get(0);
                String service = root + "." + expected.get() + "." + APPLICATION + "." + SERVICE;
                if (!isIn(implementation.getPackageName(), service)) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " is implemented by " + implementation.getName() + ", not under "
                                    + service));
                }
                String name = expectedName.apply(item);
                if (!implementation.getSimpleName().equals(name)) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " is implemented by " + implementation.getName() + ", not by " + name));
                }
            }
        };
    }

    /**
     * A sealed interface whose permitted subclasses are all records or enums: the variants of a
     * fact, which no service implements. One that permits a plain class is a port like any other.
     */
    private static boolean modelsAFactsVariants(JavaClass type) {
        return type.isSealed()
                && type.getPermittedSubclasses()
                        .map(permitted ->
                                permitted.stream().allMatch(variant -> variant.isRecord() || variant.isEnum()))
                        .orElse(false);
    }

    /**
     * The {@code XService} name ADR-120 decision 5 gives the implementation of an interface {@code
     * X}.
     */
    private static String serviceNameOf(JavaClass type) {
        return type.getSimpleName() + SERVICE_SUFFIX;
    }

    /**
     * Whether a target is outside every source module, or in, or below, a source module's {@code
     * events}.
     */
    private static boolean isNoSourceOrItsEvents(String root, Set<String> sources, JavaClass target) {
        String[] segments = segmentsBelow(root, target.getPackageName());
        return segments.length == 0
                || !sources.contains(segments[0])
                || (segments.length >= 2 && segments[1].equals(EVENTS));
    }

    /**
     * Whether a target is outside every narrowed module's {@code api}, or is a type the map names
     * for the module whose {@code api} holds it.
     */
    private static boolean isOutsideOrNamedInANarrowedApi(
            String root, Map<String, Set<String>> narrowedApis, JavaClass target) {
        String[] segments = segmentsBelow(root, target.getPackageName());
        boolean inANarrowedApi = segments.length >= 3
                && narrowedApis.containsKey(segments[0])
                && segments[1].equals(APPLICATION)
                && segments[2].equals(API);
        return !inANarrowedApi || narrowedApis.get(segments[0]).contains(nameBelow(root, target));
    }

    /**
     * Whether a class lies in, or below, {@code web} or a module's {@code adapter.in.web}, where
     * rule 14 lets it read the call's holders.
     */
    private static boolean mayReadTheCallsHolders(String root, JavaClass type) {
        String[] segments = segmentsBelow(root, type.getPackageName());
        return (segments.length >= 1 && segments[0].equals(WEB))
                || (segments.length >= 4
                        && !NOT_MODULES.contains(segments[0])
                        && segments[1].equals(ADAPTER)
                        && segments[2].equals(IN)
                        && segments[3].equals(WEB));
    }

    /**
     * Whether a target is in no module, or in, or below, a module's {@code application.api} or
     * {@code events}.
     */
    private static boolean isNoModuleOrItsApiOrEvents(String root, JavaClass target) {
        String[] segments = segmentsBelow(root, target.getPackageName());
        return segments.length == 0 || NOT_MODULES.contains(segments[0]) || isInAnApiOrEvents(segments);
    }

    /**
     * Whether a package, by its segments below the root, is in, or below, a top-level package's
     * {@code application.api} or {@code events}.
     */
    private static boolean isInAnApiOrEvents(String[] segments) {
        return (segments.length >= 2 && segments[1].equals(EVENTS))
                || (segments.length >= 3 && segments[1].equals(APPLICATION) && segments[2].equals(API));
    }

    /** Whether a module of the graph reaches itself by following its arrows. */
    private static boolean reachesItself(Map<String, Set<String>> graph, String module) {
        Set<String> visited = new HashSet<>();
        Deque<String> toVisit = new ArrayDeque<>(graph.getOrDefault(module, Set.of()));
        while (!toVisit.isEmpty()) {
            String next = toVisit.pop();
            if (next.equals(module)) {
                return true;
            }
            if (visited.add(next)) {
                toVisit.addAll(graph.getOrDefault(next, Set.of()));
            }
        }
        return false;
    }

    /**
     * A condition that reports, against the class, each dependency whose target {@code allowed}
     * refuses.
     */
    private static ArchCondition<JavaClass> dependOnlyOn(String description, AllowedTarget allowed) {
        return new ArchCondition<>("depend only on " + description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> {
                            JavaClass target = baseType(dependency.getTargetClass());
                            return !target.isPrimitive() && !allowed.test(item, target);
                        })
                        .forEach(dependency ->
                                events.add(SimpleConditionEvent.violated(item, dependency.getDescription())));
            }
        };
    }

    /**
     * Whether a class may depend on a target, the target's arrays already reduced to their element.
     */
    @FunctionalInterface
    private interface AllowedTarget {
        boolean test(JavaClass item, JavaClass target);
    }

    /**
     * The first package segment below the root, such as {@code planning}; empty for the root's own
     * classes.
     */
    private static Optional<String> topLevelPackage(String root, JavaClass type) {
        String[] segments = segmentsBelow(root, type.getPackageName());
        return segments.length == 0 ? Optional.empty() : Optional.of(segments[0]);
    }

    /** The class's name below the root, such as {@code planning.application.spi.ExecutionFacts}. */
    private static String nameBelow(String root, JavaClass type) {
        return type.getName().substring(root.length() + 1);
    }

    private static JavaClass baseType(JavaClass type) {
        return type.isArray() ? type.getBaseComponentType() : type;
    }

    /** A module's {@code domain} package, or below it. */
    private static boolean isInAModulesDomain(String root, String packageName) {
        String[] segments = segmentsBelow(root, packageName);
        return segments.length >= 2 && segments[1].equals(DOMAIN);
    }

    /**
     * The package's segments below the root; none for a package outside the root or the root
     * itself.
     */
    private static String[] segmentsBelow(String root, String packageName) {
        return packageName.startsWith(root + ".")
                ? packageName.substring(root.length() + 1).split("\\.")
                : new String[0];
    }

    private static boolean isIn(String packageName, String candidate) {
        return packageName.equals(candidate) || packageName.startsWith(candidate + ".");
    }

    private static boolean isInAny(String packageName, List<String> packages) {
        return packages.stream().anyMatch(candidate -> isIn(packageName, candidate));
    }
}
