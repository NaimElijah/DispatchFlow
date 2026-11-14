package com.dispatchflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.dispatchflow.shared.AggregateRoot;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The domain purity rules of DL §3.9 and the optimizer-result rule of DL §13, built for a root
 * package. {@code com.dispatchflow} checks production code; {@code com.dispatchflow.archfixture}
 * checks the fixtures that prove each rule fires.
 *
 * <p>Every rule's subject is the kernel plus every module's {@code domain} and {@code events}
 * found by package pattern, so a module added later is covered without editing a rule, and no
 * subject is empty before the first module exists.
 */
final class DomainPurityRules {

    private static final String KERNEL = "shared";
    private static final String DOMAIN = "domain";
    private static final String EVENTS = "events";
    private static final String PACKAGE_INFO = "package-info";
    private static final List<String> KERNEL_LIBRARIES = List.of("com.google.i18n.phonenumbers");
    private static final List<String> DOMAIN_LIBRARIES =
            List.of("com.google.i18n.phonenumbers", "org.locationtech.jts");
    private static final List<String> ALWAYS_ALLOWED = List.of("java", "org.jspecify");
    // M0 puts @ApplicationModule and @NamedInterface on these packages' package-info (ADR-040).
    private static final String MODULITH = "org.springframework.modulith";

    // A legacy date type or a Clock is a way to read the time inside the domain.
    private static final List<String> CLOCK_TYPES = List.of(
            "java.util.Date", "java.util.Calendar", "java.util.TimeZone", "java.time.Clock", "java.time.InstantSource");

    private static final List<String> IO_PACKAGES = List.of(
            "java.io",
            "java.nio.file",
            "java.nio.channels",
            "java.net",
            "java.sql",
            "javax.sql",
            "java.rmi",
            "javax.net");
    // Not I/O: java.net.IDN converts email domains to ASCII (BR-45); Serializable and Serial only
    // mark types.
    private static final Set<String> IO_EXCEPTIONS = Set.of("java.net.IDN", "java.io.Serializable", "java.io.Serial");
    private static final List<String> IO_CLASSES =
            List.of("java.lang.ProcessBuilder", "java.lang.Process", "java.lang.ProcessHandle", "java.lang.Runtime");
    private static final Set<String> IO_SYSTEM_MEMBERS =
            Set.of("in", "out", "err", "console", "getenv", "getProperty", "getProperties", "setProperty");

    private static final List<String> LOGGING_PACKAGES = List.of(
            "java.util.logging", "org.slf4j", "org.apache.logging", "org.apache.commons.logging", "ch.qos.logback");
    private static final String SYSTEM_LOGGER = "java.lang.System$Logger";

    private static final List<String> RANDOM_PACKAGES = List.of("java.util.random");
    private static final List<String> RANDOM_CLASSES = List.of(
            "java.util.Random",
            "java.security.SecureRandom",
            "java.util.SplittableRandom",
            "java.util.concurrent.ThreadLocalRandom");

    private static final Set<String> GENERIC_EXCEPTIONS =
            Set.of(IllegalArgumentException.class.getName(), IllegalStateException.class.getName());
    private static final String CONSTRUCTOR = "<init>";

    private static final String PERSISTENCE_ADAPTER = "..adapter.out.persistence..";
    private static final String OPTIMIZER_ADAPTER = "..planning.adapter.out.optimizer..";
    private static final Set<String> OPTIMIZER_RESULTS = Set.of("ValidationReport", "Evaluation");

    private DomainPurityRules() {}

    /**
     * Rule 1: the kernel, each domain and each events package depend only on what DL §3.9 and §7.3
     * allow, with Spring Modulith's annotations allowed on their {@code package-info} only.
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
                .resideInAnyPackage(pureLayers(root))
                .should(dependOnlyOnAllowedPackages(root, moduleAnnotations))
                .because("DL §3.9 rule 1 and §7.3: the kernel imports only the JDK, JSpecify and libphonenumber;"
                        + " a domain also its own domain and events, the kernel and JTS; events only the JDK,"
                        + " JSpecify, the kernel and their own package");
    }

    /** Rule 2: no clock. */
    static ArchRule noSystemClock(String root) {
        return classes()
                .that()
                .resideInAnyPackage(pureLayers(root))
                .should(notAccess("read the system clock", DomainPurityRules::readsTheClock)
                        .and(notDependOn("a legacy date type or a clock", DomainPurityRules::isAClock)))
                .because("DL §3.9 rule 2: now and today are parameters (NFR-13, NFR-175)");
    }

    /** Rule 3: no ID generation. */
    static ArchRule noIdGeneration(String root) {
        return classes()
                .that()
                .resideInAnyPackage(pureLayers(root))
                .should(notAccess("generate a random UUID", DomainPurityRules::generatesAnId))
                .because("DL §3.9 rule 3: factories take the new ID from IdGenerator");
    }

    /** Rule 4: no randomness. */
    static ArchRule noRandomness(String root) {
        return classes()
                .that()
                .resideInAnyPackage(pureLayers(root))
                .should(notDependOn("a source of randomness", DomainPurityRules::isRandomness)
                        .and(notAccess("call a random method", DomainPurityRules::callsRandomness)))
                .because("DL §3.9 rule 4: the domain uses no randomness");
    }

    /** Rule 4: no I/O. */
    static ArchRule noIo(String root) {
        return classes()
                .that()
                .resideInAnyPackage(pureLayers(root))
                .should(notDependOn("an I/O type", DomainPurityRules::isIo)
                        .and(notAccess("use the process's streams or environment", DomainPurityRules::usesTheProcess)))
                .because("DL §3.9 rule 4: the domain does no I/O");
    }

    /** Rule 4: no logging. */
    static ArchRule noLogging(String root) {
        return classes()
                .that()
                .resideInAnyPackage(pureLayers(root))
                .should(notDependOn("a logging type", DomainPurityRules::isLogging))
                .because("DL §3.9 rule 4: the domain does no logging");
    }

    /** Rule 5: no generic exceptions. */
    static ArchRule noGenericExceptions(String root) {
        return classes()
                .that()
                .resideInAnyPackage(pureLayers(root))
                .should(notAccess(
                        "create an IllegalArgumentException or IllegalStateException",
                        DomainPurityRules::createsAGenericException))
                .because("DL §3.8 and §3.9 rule 5: a refusal is a named error");
    }

    /** Rule 6: only persistence adapters rebuild aggregates and read the lock-read snapshots. */
    static ArchRule restoreOnlyFromPersistence(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .resideOutsideOfPackage(PERSISTENCE_ADAPTER)
                .should(notAccess(
                        "call an aggregate's static restore or a readUnderLock factory",
                        DomainPurityRules::rebuildsStoredState))
                .because("DL §3.5 and §3.9 rule 6: only persistence adapters call restore and readUnderLock");
    }

    /** DL §13: only the optimizer adapter builds a ValidationReport or an Evaluation. */
    static ArchRule optimizerResultsOnlyFromOptimizerAdapter(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .resideOutsideOfPackage(OPTIMIZER_ADAPTER)
                .should(notAccess(
                        "call a static factory of ValidationReport or Evaluation",
                        DomainPurityRules::buildsAnOptimizerResult))
                .because("DL §13 and BR-15: no code path can hand Plan an unvalidated solution");
    }

    private static String[] pureLayers(String root) {
        return new String[] {root + "." + KERNEL + "..", root + ".*." + DOMAIN + "..", root + ".*." + EVENTS + ".."};
    }

    private static ArchCondition<JavaClass> dependOnlyOnAllowedPackages(String root, String moduleAnnotations) {
        return new ArchCondition<>("depend only on the packages DL §3.9 allows") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                List<String> allowed = allowedPackages(root, moduleAnnotations, item);
                item.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> {
                            JavaClass target = baseType(dependency.getTargetClass());
                            return !target.isPrimitive() && !isInAny(target.getPackageName(), allowed);
                        })
                        .forEach(dependency ->
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
            }
        };
    }

    /**
     * The packages one class may depend on, from where it lives: {@code <root>.shared...}, {@code
     * <root>.<module>.domain...} or {@code <root>.<module>.events...}. A {@code package-info} of a
     * domain or events package, or of the kernel's own package (a module root), may also carry the
     * module annotations.
     */
    private static List<String> allowedPackages(String root, String moduleAnnotations, JavaClass item) {
        String[] segments = item.getPackageName().substring(root.length() + 1).split("\\.");
        List<String> allowed = new ArrayList<>(ALWAYS_ALLOWED);
        allowed.add(root + "." + KERNEL);
        boolean packageInfo = item.getSimpleName().equals(PACKAGE_INFO);
        if (segments[0].equals(KERNEL)) {
            allowed.addAll(KERNEL_LIBRARIES);
            if (packageInfo && segments.length == 1) {
                allowed.add(moduleAnnotations);
            }
            return allowed;
        }
        String module = root + "." + segments[0];
        allowed.add(module + "." + EVENTS);
        if (segments[1].equals(DOMAIN)) {
            allowed.add(module + "." + DOMAIN);
            allowed.addAll(DOMAIN_LIBRARIES);
        }
        if (packageInfo) {
            allowed.add(moduleAnnotations);
        }
        return allowed;
    }

    private static ArchCondition<JavaClass> notAccess(String description, Predicate<JavaAccess<?>> forbidden) {
        return new ArchCondition<>("not " + description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getAccessesFromSelf().stream()
                        .filter(forbidden)
                        .forEach(access -> events.add(SimpleConditionEvent.violated(access, access.getDescription())));
            }
        };
    }

    private static ArchCondition<JavaClass> notDependOn(String description, Predicate<JavaClass> forbidden) {
        return new ArchCondition<>("not depend on " + description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> forbidden.test(baseType(dependency.getTargetClass())))
                        .forEach(dependency ->
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
            }
        };
    }

    private static boolean readsTheClock(JavaAccess<?> access) {
        AccessTarget target = access.getTarget();
        String owner = target.getOwner().getName();
        String name = target.getName();
        return (owner.startsWith("java.time.") && name.equals("now"))
                || (owner.equals("java.time.Clock") && name.startsWith("system"))
                || (owner.equals("java.time.InstantSource") && name.equals("system"))
                || (owner.equals("java.lang.System") && (name.equals("currentTimeMillis") || name.equals("nanoTime")))
                || (owner.equals("java.time.ZoneId") && name.equals("systemDefault"))
                || (owner.equals("java.util.TimeZone") && name.equals("getDefault"));
    }

    private static boolean isAClock(JavaClass type) {
        return CLOCK_TYPES.stream().anyMatch(type::isAssignableTo);
    }

    private static boolean generatesAnId(JavaAccess<?> access) {
        return access.getTarget().getOwner().getName().equals("java.util.UUID")
                && access.getTarget().getName().equals("randomUUID");
    }

    private static boolean isRandomness(JavaClass type) {
        return isInAny(type.getPackageName(), RANDOM_PACKAGES) || isAnyOf(type, RANDOM_CLASSES);
    }

    private static boolean callsRandomness(JavaAccess<?> access) {
        String owner = access.getTarget().getOwner().getName();
        String name = access.getTarget().getName();
        return ((owner.equals("java.lang.Math") || owner.equals("java.lang.StrictMath")) && name.equals("random"))
                || (owner.equals("java.util.Collections") && name.equals("shuffle"));
    }

    private static boolean isIo(JavaClass type) {
        if (IO_EXCEPTIONS.contains(type.getName())) {
            return false;
        }
        return isInAny(type.getPackageName(), IO_PACKAGES) || isAnyOf(type, IO_CLASSES);
    }

    private static boolean usesTheProcess(JavaAccess<?> access) {
        return access.getTarget().getOwner().getName().equals("java.lang.System")
                && IO_SYSTEM_MEMBERS.contains(access.getTarget().getName());
    }

    private static boolean isLogging(JavaClass type) {
        return isInAny(type.getPackageName(), LOGGING_PACKAGES) || isAnyOf(type, List.of(SYSTEM_LOGGER));
    }

    /** Subclasses count too: a NumberFormatException is an IllegalArgumentException. */
    private static boolean createsAGenericException(JavaAccess<?> access) {
        JavaClass owner = access.getTarget().getOwner();
        return access.getTarget().getName().equals(CONSTRUCTOR)
                && GENERIC_EXCEPTIONS.stream().anyMatch(owner::isAssignableTo);
    }

    /**
     * Matches by name, so a later module's aggregate or snapshot is covered without editing this
     * rule. A type calling its own factory doesn't widen who can build it, so that is allowed.
     */
    private static boolean rebuildsStoredState(JavaAccess<?> access) {
        AccessTarget target = access.getTarget();
        boolean restoresAnAggregate = target.getName().equals("restore")
                && target.getOwner().isAssignableTo(AggregateRoot.class)
                && isStatic(target);
        return (restoresAnAggregate || target.getName().equals("readUnderLock")) && !isSelfAccess(access);
    }

    /**
     * A constructor or a static factory, called or referenced. A record-shaped or package-private
     * constructor is caught here too; {@link DomainShapeRules} also requires the constructors to be
     * private.
     */
    private static boolean buildsAnOptimizerResult(JavaAccess<?> access) {
        AccessTarget target = access.getTarget();
        return access instanceof JavaCodeUnitAccess<?>
                && OPTIMIZER_RESULTS.contains(target.getOwner().getSimpleName())
                && (target.getName().equals(CONSTRUCTOR) || isStatic(target))
                && !isSelfAccess(access);
    }

    private static boolean isStatic(AccessTarget target) {
        // An unresolvable target is treated as static, so the rule errs toward flagging.
        return target.resolveMember()
                .map(member -> member.getModifiers().contains(JavaModifier.STATIC))
                .orElse(true);
    }

    /** An access from the target type itself, or from a type nested in it at any depth. */
    private static boolean isSelfAccess(JavaAccess<?> access) {
        JavaClass target = access.getTargetOwner();
        Optional<JavaClass> origin = Optional.of(access.getOriginOwner());
        while (origin.isPresent()) {
            if (origin.get().equals(target)) {
                return true;
            }
            origin = origin.get().getEnclosingClass();
        }
        return false;
    }

    private static JavaClass baseType(JavaClass type) {
        return type.isArray() ? type.getBaseComponentType() : type;
    }

    private static boolean isAnyOf(JavaClass type, List<String> classNames) {
        return classNames.stream()
                .anyMatch(name -> type.getName().equals(name) || type.getName().startsWith(name + "$"));
    }

    private static boolean isInAny(String packageName, List<String> packages) {
        return packages.stream()
                .anyMatch(candidate -> packageName.equals(candidate) || packageName.startsWith(candidate + "."));
    }
}
