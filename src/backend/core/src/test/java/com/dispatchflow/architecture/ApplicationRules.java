package com.dispatchflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
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
    private static final String PACKAGE_INFO = "package-info";
    private static final List<String> ALWAYS_ALLOWED = List.of("java", "org.jspecify");
    // The pure domain libraries reach the application through the domain types it handles.
    private static final List<String> DOMAIN_LIBRARIES =
            List.of("com.google.i18n.phonenumbers", "org.locationtech.jts");
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

    /** The top-level packages under the root that are no module: the kernel and the web concerns. */
    static final Set<String> NOT_MODULES = Set.of(KERNEL, WEB);

    private ApplicationRules() {}

    /**
     * Rule 1: each module's application layer and the shared application package depend only on
     * what TS §2.2 and DL §2.3 allow, with Spring Modulith's annotations allowed on their {@code
     * package-info} only.
     */
    static ArchRule onlyAllowedDependencies(String root) {
        return onlyAllowedDependencies(root, MODULITH);
    }

    /**
     * Rule 1, with the package whose annotations a {@code package-info} may carry given: Spring
     * Modulith's for production, a stand-in for the fixtures, because Modulith is not on the
     * classpath before M0.
     */
    static ArchRule onlyAllowedDependencies(String root, String moduleAnnotations) {
        return classes()
                .that()
                .resideInAPackage(applicationLayers(root))
                .should(dependOnlyOn(
                        "the packages TS §2.2 allows the application",
                        (item, target) -> isAllowedInTheApplication(root, moduleAnnotations, item, target)))
                .because("TS §2.2 and DL §2.3: the application imports the JDK, JSpecify, the kernel, its own"
                        + " module, other modules' api, spi and events, the domain's libraries, and from"
                        + " Spring only @Transactional");
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
                .should(haveOneImplementation(root, true))
                .because("TS §4: an api interface X is implemented once, by its own module's XService");
    }

    /**
     * Rule 4, for {@code spi}: every non-sealed interface in a module's {@code application.spi} has
     * exactly one concrete implementation, outside its own module. Sealed interfaces are skipped.
     */
    static ArchRule eachSpiHasOneImplementationOutsideItsModule(String root) {
        return classes()
                .that()
                .resideInAPackage(root + ".*." + APPLICATION + "." + SPI + "..")
                .should(haveOneImplementation(root, false))
                .because("DL §2.3 and TS §4: a module owns an spi for exactly one other module to implement");
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

    /** Each module's application layer, with the shared application package. */
    private static String applicationLayers(String root) {
        return root + ".*." + APPLICATION + "..";
    }

    private static Stream<JavaPackage> applicationPackages(String root, JavaClasses classes) {
        return classes.that(JavaClass.Predicates.resideInAPackage(applicationLayers(root))).stream()
                .map(JavaClass::getPackage);
    }

    /**
     * What a class of {@code <root>.<module>.application} may depend on. Another module's {@code
     * api}, {@code spi} and {@code events} are allowed here whatever the module; rule 2 decides
     * which modules.
     */
    private static boolean isAllowedInTheApplication(
            String root, String moduleAnnotations, JavaClass item, JavaClass target) {
        String targetPackage = target.getPackageName();
        if (isInAny(targetPackage, ALWAYS_ALLOWED)
                || isInAny(targetPackage, DOMAIN_LIBRARIES)
                || isIn(targetPackage, root + "." + KERNEL)
                || TRANSACTIONAL.contains(target.getName())
                || (item.getSimpleName().equals(PACKAGE_INFO) && isIn(targetPackage, moduleAnnotations))) {
            return true;
        }
        String module = root + "." + topLevelPackage(root, item).orElseThrow();
        if (isIn(targetPackage, module + "." + DOMAIN)
                || isIn(targetPackage, module + "." + EVENTS)
                || isIn(targetPackage, module + "." + APPLICATION)) {
            return true;
        }
        return isAPublishedPackageOfAModule(root, targetPackage);
    }

    /** A module's {@code application.api}, {@code application.spi} or {@code events}, or below one. */
    private static boolean isAPublishedPackageOfAModule(String root, String targetPackage) {
        String[] segments = segmentsBelow(root, targetPackage);
        return (segments.length >= 2 && segments[1].equals(EVENTS))
                || (segments.length >= 3
                        && segments[1].equals(APPLICATION)
                        && (segments[2].equals(API) || segments[2].equals(SPI)));
    }

    private static ArchCondition<JavaClass> haveOneImplementation(String root, boolean inItsOwnModule) {
        String where = inItsOwnModule ? "in its own module" : "outside its own module";
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
                boolean sameModule = topLevelPackage(root, implementation).equals(topLevelPackage(root, item));
                if (sameModule != inItsOwnModule) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " is implemented by " + implementation.getName() + ", not " + where));
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
