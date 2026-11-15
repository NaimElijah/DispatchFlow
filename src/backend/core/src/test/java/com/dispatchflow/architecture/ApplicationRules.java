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
 * The application layer's rules (TS §2.2, TS §4, DL §2.1, DL §2.3), built for a root package as
 * {@link DomainPurityRules} is. {@code com.dispatchflow} checks production code; {@code
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
    private static final String SERVICE = "service";
    private static final String PORT_OUT = "port.out";
    private static final String PACKAGE_INFO = "package-info";
    private static final List<String> ALWAYS_ALLOWED = List.of("java", "org.jspecify");
    // TS §2.2: the one Spring annotation the application may carry, so a use case can mark its
    // transaction. ArchUnit counts an annotation's members, defaults included, as dependencies, so
    // even a bare @Transactional names Propagation and Isolation; ADR-110's REQUIRES_NEW needs them too.
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
     * DL §2.1's dashed arrows: each {@code spi} interface, by its name below the root, and the module
     * that implements it. An interface missing here fails {@link
     * #eachSpiHasOneImplementationInItsArrowsModule}, so a new {@code spi} joins the context map on
     * purpose.
     */
    static final Map<String, String> SPI_IMPLEMENTERS = Map.of(
            "planning.application.spi.ExecutionFacts", "liveops",
            "orders.application.spi.StopProgress", "liveops",
            "orders.application.spi.OrderPlacement", "planning",
            "orders.application.spi.RecordUsage", "planning",
            "fleet.application.spi.RecordUsage", "planning");

    /** The top-level packages under the root that are no module: the kernel and the web concerns. */
    static final Set<String> NOT_MODULES = Set.of(KERNEL, WEB);

    private ApplicationRules() {}

    /**
     * Rule 1: each module's application layer and the shared application package depend only on
     * what TS §2.2 and DL §2.3 allow, with Spring Modulith's annotations allowed on their {@code
     * package-info} only. A module's own {@code adapter} is not among them: dependencies point from
     * the adapters to the application, never back. An interface of another module's {@code spi} that
     * a dashed arrow of {@link #SPI_IMPLEMENTERS} names is open only to the module that arrow names to
     * implement it; the rest of that {@code spi}, the records beside the interfaces, is open to every
     * module an arrow names to implement one of its interfaces.
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
     * @param implementers each {@code spi} interface, by its name below the root, and the module that
     *     implements it
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
     * top-level package under the root, other than the kernel and {@code web}, belongs to the module
     * of that name. A module missing from the graph fails for each of its classes.
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

    /** Rule 3: an {@code api} or {@code spi} package uses no domain type, its own module's included. */
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
     * Rule 4, for {@code api}: every non-sealed interface in a module's {@code application.api} has
     * exactly one concrete implementation, in its own module. A sealed interface models the
     * variants of a fact, which its records implement, and is skipped.
     */
    static ArchRule eachApiHasOneImplementationInItsModule(String root) {
        return classes()
                .that()
                .resideInAPackage(root + ".*." + APPLICATION + "." + API + "..")
                .should(haveOneImplementation(root, "in its own module", item -> topLevelPackage(root, item)))
                .because("TS §4: an api interface X is implemented once, by its own module's XService");
    }

    /** Rule 4, for {@code spi}, with production's dashed arrows, {@link #SPI_IMPLEMENTERS}. */
    static ArchRule eachSpiHasOneImplementationInItsArrowsModule(String root) {
        return eachSpiHasOneImplementationInItsArrowsModule(root, SPI_IMPLEMENTERS);
    }

    /**
     * Rule 4, for {@code spi}, with the dashed arrows given, as rule 2 takes its graph: every
     * non-sealed interface in a module's {@code application.spi} has exactly one concrete
     * implementation, in the module its arrow names. An interface with no arrow fails. Sealed
     * interfaces are skipped.
     *
     * @param implementers each {@code spi} interface, by its name below the root, and the module that
     *     implements it
     */
    static ArchRule eachSpiHasOneImplementationInItsArrowsModule(String root, Map<String, String> implementers) {
        return classes()
                .that()
                .resideInAPackage(root + ".*." + APPLICATION + "." + SPI + "..")
                .should(haveOneImplementation(
                        root,
                        "in the module its dashed arrow names",
                        item -> Optional.ofNullable(implementers.get(nameBelow(root, item)))))
                .because("DL §2.1 and DL §2.3: a module owns an spi for exactly the one module its dashed"
                        + " arrow names to implement");
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
     * One slice per package under {@code <root>.<module>.application}, the shared application package
     * included; every other class is left out, as {@link DomainPackageRules#domainPackages} does for
     * the domain.
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
     * Rule 7, for cycles: no cycle between application packages. Each package is a slice of its own,
     * so a cycle across modules would show too, which rule 2's acyclic graph already forbids.
     */
    static ArchRule applicationPackagesAreFreeOfCycles(String root) {
        return slices().assignedFrom(applicationPackageSlices(root))
                .should()
                .beFreeOfCycles()
                .because("ADR-118 rule 7, carried to the application: the use-case and port packages of"
                        + " each aggregate use the roots and each other one way only");
    }

    /**
     * Rule 7, for the roots: no class directly in a module's {@code application.service} or {@code
     * application.port.out} uses a class in a package under its own. The cycle rule can't see this
     * when the subpackage never names the root, and rule 1 lets any class reach its own module's
     * application.
     */
    static ArchRule serviceAndPortRootsUseNoSubpackage(String root) {
        String layer = root + ".*." + APPLICATION + ".";
        return classes()
                .that()
                .resideInAnyPackage(layer + SERVICE, layer + PORT_OUT)
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
                .because("ADR-118 rule 2, carried to the application: the service and port.out roots hold what"
                        + " several aggregates' packages use, and import none of them");
    }

    /** Each module's application layer, with the shared application package. */
    private static String applicationLayers(String root) {
        return root + ".*." + APPLICATION + "..";
    }

    private static Stream<JavaPackage> applicationPackages(String root, JavaClasses classes) {
        return classes.that(JavaClass.Predicates.resideInAPackage(applicationLayers(root))).stream()
                .map(JavaClass::getPackage);
    }

    /**
     * What a class of {@code <root>.<module>.application} may depend on. Another module's {@code api}
     * and {@code events} are allowed here whatever the module, and rule 2 decides which modules.
     * Another module's {@code spi} is allowed only as {@link #isPublishedToTheModule} says.
     */
    private static boolean isAllowedInTheApplication(
            String root, String moduleAnnotations, Map<String, String> implementers, JavaClass item, JavaClass target) {
        String targetPackage = target.getPackageName();
        if (isInAny(targetPackage, ALWAYS_ALLOWED)
                || isIn(targetPackage, root + "." + KERNEL)
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
     * application.spi}, or below it, published to {@code itemModule} by a dashed arrow. An interface
     * an arrow names is published only to the module that arrow names, so an implementer of one
     * interface can't call another module's. Every other class there, such as a record beside the
     * interfaces, is published to each module an arrow names for one of that spi's interfaces,
     * because an implementation uses the records its interface takes and returns.
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

    /** Whether a dashed arrow names {@code module} to implement an interface of {@code owner}'s spi. */
    private static boolean implementsAnSpiOf(String owner, String module, Map<String, String> implementers) {
        String ownersSpi = owner + "." + APPLICATION + "." + SPI + ".";
        return implementers.entrySet().stream()
                .anyMatch(arrow ->
                        arrow.getKey().startsWith(ownersSpi) && arrow.getValue().equals(module));
    }

    /**
     * Exactly one concrete implementation of each non-sealed interface, in the module {@code
     * expectedModule} names for it; an interface it names no module for fails.
     */
    private static ArchCondition<JavaClass> haveOneImplementation(
            String root, String where, Function<JavaClass, Optional<String>> expectedModule) {
        return new ArchCondition<>(
                "have exactly one concrete implementation " + where + " when a non-sealed interface") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                // A package-info compiles to a synthetic interface, which no class implements.
                if (!item.isInterface()
                        || item.isAnnotation()
                        || item.isSealed()
                        || item.getSimpleName().equals(PACKAGE_INFO)) {
                    return;
                }
                Optional<String> expected = expectedModule.apply(item);
                if (expected.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(
                            item, item.getName() + " names no module to implement it, so none is " + where));
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
                if (!topLevelPackage(root, implementation).equals(expected)) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " is implemented by " + implementation.getName() + ", not in module "
                                    + expected.get()));
                }
            }
        };
    }

    /** A condition that reports, against the class, each dependency whose target {@code allowed} refuses. */
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

    /** Whether a class may depend on a target, the target's arrays already reduced to their element. */
    @FunctionalInterface
    private interface AllowedTarget {
        boolean test(JavaClass item, JavaClass target);
    }

    /** The first package segment below the root, such as {@code planning}; empty for the root's own classes. */
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

    /** The package's segments below the root; none for a package outside the root or the root itself. */
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
