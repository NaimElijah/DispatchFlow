package com.dispatchflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
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
 * The application layer's rules (TS §2.2, TS §4, DL §2.1, DL §2.3, ADR-120), and the adapters'
 * (TS §2.1, ADR-130), built for a root package as {@link DomainPurityRules} is. {@code
 * com.dispatchflow} checks production code; {@code com.dispatchflow.archfixture.zeta} checks the
 * fixtures that prove each rule fires.
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
    private static final List<String> ALWAYS_ALLOWED = List.of("java", "org.jspecify");
    // TS §2.2: the one Spring annotation the application may carry, so a use case can mark its
    // transaction. ArchUnit counts an annotation's members, defaults included, as dependencies, so
    // even a bare @Transactional names Propagation and Isolation; ADR-110's REQUIRES_NEW needs them
    // too.
    private static final Set<String> TRANSACTIONAL = Set.of(
            "org.springframework.transaction.annotation.Transactional",
            "org.springframework.transaction.annotation.Propagation",
            "org.springframework.transaction.annotation.Isolation");
    // M0 puts @ApplicationModule and @NamedInterface on these packages' package-info (ADR-040).
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
     * names below the root, that it may use: of Access, the {@code Actor} a web call acts for and
     * its roles, and nothing that reads an account or the company (ADR-120 P5).
     */
    static final Map<String, Set<String>> REPORTING_NARROWED_APIS =
            Map.of("access", Set.of("access.application.api.Actor", "access.application.api.ActorRole"));

    /**
     * The top-level packages under the root that are no module: the kernel and the web concerns.
     */
    static final Set<String> NOT_MODULES = Set.of(KERNEL, WEB);

    /**
     * ADR-130 decision 2: the kinds of inbound adapter, {@code <module>.adapter.in.<kind>}. A use
     * case starts from a screen ({@code web}), another module's event ({@code listen}), the clock
     * ({@code schedule}), queued work ({@code job}), an outside service calling in ({@code
     * callback}) or a person at a prompt ({@code cli}).
     */
    static final Set<String> INBOUND_KINDS = Set.of("web", "listen", "schedule", "job", "callback", "cli");

    /**
     * ADR-130 decision 4 and ADR-131: each module's adapters, and the kernel's, by their names
     * below its {@code adapter} package, such as {@code in.web} or {@code out.persistence}. A
     * subpackage of an adapter, such as {@code out.persistence.plan}, counts as that adapter. The
     * tree must hold exactly these, so a new adapter, or a dropped one, is declared here on
     * purpose, as a new module joins {@link #MODULE_GRAPH}.
     */
    static final Map<String, Set<String>> DECLARED_ADAPTERS = Map.of(
            "access",
            Set.of(
                    "in.web",
                    "in.listen",
                    "in.schedule",
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
            Set.of("out.persistence", "out.clock", "out.uuid", "out.optimizer"));

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
     * classpath before M0. The dashed arrows are given too, as rule 4 takes them.
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
     * {@code web} at all, which it refuses until the app-wide {@code web} ADR decides otherwise
     * (ADR-130 decisions 6 and 7).
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
     * Rule 11: the adapters beside the kernel use no module. A class in, or below, {@code
     * shared.adapter} depends on no class of a top-level package other than the kernel and {@code
     * web}. Rule 2 skips the kernel's classes and rules 9 and 10 see only adapters, so without this
     * rule one module could reach another through a shared adapter, along an arrow DL §2.1 lacks
     * (ADR-130 decision 7).
     */
    static ArchRule sharedAdaptersUseNoModule(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "." + KERNEL + "." + ADAPTER + "..")
                .should(dependOnlyOn(
                        "no module",
                        (item, target) -> topLevelPackage(root, target)
                                .map(NOT_MODULES::contains)
                                .orElse(true)))
                .because("DL §2.1 and ADR-130 decision 7: every module depends on the kernel and never the"
                        + " reverse, so a shared adapter that used a module would open an arrow the graph lacks");
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
                .map(adapter -> adapter.split("\\.", ADAPTER_SEGMENTS - 1))
                .collect(Collectors.groupingBy(
                        adapter -> adapter[0],
                        Collectors.mapping(
                                adapter -> adapter.length == ADAPTER_SEGMENTS - 1 ? adapter[2] : "",
                                Collectors.toSet())));
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
        if (segments.length >= 2 && segments[1].equals(EVENTS)) {
            return true;
        }
        if (segments.length < 3 || !segments[1].equals(APPLICATION)) {
            return false;
        }
        if (segments[2].equals(API)) {
            return true;
        }
        if (!segments[2].equals(SPI)) {
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
