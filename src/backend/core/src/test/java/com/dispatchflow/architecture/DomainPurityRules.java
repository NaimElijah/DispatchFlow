package com.dispatchflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final String APPLICATION = "application";
    private static final String ADAPTER = "adapter";
    // ADR-126 and ADR-130: the adapters every module shares, under shared.adapter.in.<x> and
    // shared.adapter.out.<x>, sit beside the kernel. They are no part of it, so no purity rule holds
    // them, and nothing pure may use them.
    private static final List<String> SHARED_ADAPTER_DIRECTIONS = List.of("in", "out");
    private static final String PACKAGE_INFO = "package-info";
    private static final List<String> KERNEL_LIBRARIES = List.of("com.google.i18n.phonenumbers");
    private static final List<String> DOMAIN_LIBRARIES =
            List.of("com.google.i18n.phonenumbers", "org.locationtech.jts");
    private static final List<String> ALWAYS_ALLOWED = List.of("java", "org.jspecify");
    // M0 puts @ApplicationModule and @NamedInterface on these packages' package-info (ADR-040), and
    // @NamedInterface alone on shared.application's (ADR-120 decision 2).
    private static final String MODULITH = "org.springframework.modulith";
    // The fixtures' stand-in keeps the simple name, so one name serves Modulith and the stand-in.
    private static final String NAMED_INTERFACE = "NamedInterface";
    // ADR-126: a domain or kernel class may carry Jakarta Persistence's annotations, with the enums
    // they take as values (such as EnumType), and these four from Hibernate and Spring Data. Only
    // annotations: never an EntityManager, a Spring Data type or another Hibernate annotation.
    private static final String JAKARTA_PERSISTENCE = "jakarta.persistence";
    private static final Set<String> PERSISTENCE_ANNOTATIONS = Set.of(
            "org.hibernate.annotations.OptimisticLock",
            "org.hibernate.annotations.Immutable",
            "org.springframework.data.domain.DomainEvents",
            "org.springframework.data.domain.AfterDomainEventPublication");

    // A legacy date type or a Clock is a way to read the time inside the domain.
    private static final List<String> CLOCK_TYPES = List.of(
            "java.util.Date", "java.util.Calendar", "java.util.TimeZone", "java.time.Clock", "java.time.InstantSource");

    // java.util.prefs reads and writes stored preferences, and java.lang.management reads the
    // running process.
    private static final List<String> IO_PACKAGES = List.of(
            "java.io",
            "java.nio.file",
            "java.nio.channels",
            "java.net",
            "java.sql",
            "javax.sql",
            "java.rmi",
            "javax.net",
            "java.util.prefs",
            "java.lang.management");
    // Not I/O: java.net.IDN converts email domains to ASCII (BR-45); Serializable and Serial only
    // mark types.
    private static final Set<String> IO_EXCEPTIONS = Set.of("java.net.IDN", "java.io.Serializable", "java.io.Serial");
    // A ResourceBundle loads its text from the classpath, and a ServiceLoader its providers.
    private static final List<String> IO_CLASSES = List.of(
            "java.lang.ProcessBuilder",
            "java.lang.Process",
            "java.lang.ProcessHandle",
            "java.lang.Runtime",
            "java.util.ResourceBundle",
            "java.util.ServiceLoader");
    private static final Set<String> IO_SYSTEM_MEMBERS =
            Set.of("in", "out", "err", "console", "getenv", "getProperty", "getProperties", "setProperty");
    // Each reads a system property by name and parses it.
    private static final Map<String, String> PROPERTY_READERS = Map.of(
            "java.lang.Integer", "getInteger",
            "java.lang.Long", "getLong",
            "java.lang.Boolean", "getBoolean");
    // The default locale is the process's environment, as a system property is.
    private static final Set<String> DEFAULT_LOCALE_MEMBERS = Set.of("getDefault", "setDefault");
    // Each of these reads the default locale when it is given none: a case change with no argument,
    // String.format whose first parameter is the template rather than a Locale, and ofPattern with
    // the pattern alone. String.formatted has no Locale overload at all.
    private static final Set<String> CASE_CHANGES = Set.of("toUpperCase", "toLowerCase");
    private static final String FORMAT = "format";
    private static final String FORMATTED = "formatted";
    private static final String OF_PATTERN = "ofPattern";
    private static final String STRING = "java.lang.String";
    private static final String DATE_TIME_FORMATTER = "java.time.format.DateTimeFormatter";
    private static final String OF_LOCALIZED = "ofLocalized";
    private static final String DATE_TIME_FORMATTER_BUILDER = "java.time.format.DateTimeFormatterBuilder";
    private static final String TO_FORMATTER = "toFormatter";
    // java.text's number, date and message formats, and the symbols they print with, are built for
    // the default locale unless one is given: by a Locale, or by symbols built for one. Only their
    // constructors and static factories choose the locale; Normalizer, also in java.text, reads
    // none and stays allowed.
    private static final List<String> LOCALE_FORMATS = List.of(
            "java.text.NumberFormat",
            "java.text.DateFormat",
            "java.text.MessageFormat",
            "java.text.DecimalFormatSymbols",
            "java.text.DateFormatSymbols");
    private static final Set<String> LOCALE_CARRIERS =
            Set.of("java.util.Locale", "java.text.DecimalFormatSymbols", "java.text.DateFormatSymbols");
    private static final String FACTORY_SUFFIX = "Instance";
    // A class, its loader or its module reads a resource from the classpath.
    private static final List<String> RESOURCE_OWNERS =
            List.of("java.lang.Class", "java.lang.ClassLoader", "java.lang.Module");
    private static final Set<String> RESOURCE_MEMBERS = Set.of(
            "getResource",
            "getResources",
            "getResourceAsStream",
            "getSystemResource",
            "getSystemResources",
            "getSystemResourceAsStream",
            "resources");
    // A thread the domain starts makes its outcome depend on the scheduler, as a Process it starts
    // depends on the operating system.
    private static final String THREAD = "java.lang.Thread";
    private static final Set<String> THREAD_BUILDERS = Set.of("ofPlatform", "ofVirtual", "startVirtualThread");
    private static final String EXECUTORS = "java.util.concurrent.Executors";
    // Every member of these counts, as each exists to run tasks on threads of its own; so does a
    // subclass, such as ScheduledThreadPoolExecutor.
    private static final List<String> THREAD_POOLS =
            List.of("java.util.concurrent.ForkJoinPool", "java.util.concurrent.ThreadPoolExecutor", "java.util.Timer");
    // An *Async method of a CompletableFuture runs on the common pool, and a parallel stream too.
    private static final String COMPLETION_STAGE = "java.util.concurrent.CompletionStage";
    private static final String ASYNC_SUFFIX = "Async";
    private static final String COLLECTION = "java.util.Collection";
    private static final String PARALLEL_STREAM = "parallelStream";
    private static final String BASE_STREAM = "java.util.stream.BaseStream";
    private static final String PARALLEL = "parallel";
    // A Cleaner starts the thread that runs its cleaning actions.
    private static final String CLEANER = "java.lang.ref.Cleaner";
    private static final String CREATE = "create";

    // Reflection reaches any member by name, so it would step past every rule here; Class.forName
    // also loads a class from the classpath.
    private static final List<String> REFLECTION_PACKAGES = List.of("java.lang.reflect");
    private static final List<String> REFLECTION_CLASSES = List.of("java.lang.invoke.MethodHandles");
    private static final String CLASS = "java.lang.Class";
    private static final String FOR_NAME = "forName";

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

    private DomainPurityRules() {}

    /**
     * Rule 1: the kernel, each domain and each events package depend only on what DL §3.9 and §7.3
     * allow, with Spring Modulith's annotations allowed on their {@code package-info} only, and on
     * the kernel's application package-info only {@code @NamedInterface}.
     */
    static ArchRule onlyAllowedDependencies(String root) {
        return onlyAllowedDependencies(root, MODULITH);
    }

    /**
     * Rule 1, with the package whose annotations a {@code package-info} may carry given: Spring
     * Modulith's for production, a stand-in for the fixtures, because Modulith is not on the
     * classpath before M0. Of that package, the kernel's application package-info may carry only
     * the {@code NamedInterface} annotation.
     */
    static ArchRule onlyAllowedDependencies(String root, String moduleAnnotations) {
        return classes()
                .that(inPureLayers(root))
                .should(dependOnlyOnAllowedPackages(root, moduleAnnotations))
                .because("DL §3.9 rule 1 and §7.3: the kernel imports only the JDK, JSpecify and libphonenumber;"
                        + " a domain also its own domain and events, the kernel and JTS; events only the JDK,"
                        + " JSpecify, the kernel and their own package; the kernel and a domain may also carry"
                        + " ADR-126's persistence annotations; none of them uses a shared adapter");
    }

    /** Rule 2: no clock. */
    static ArchRule noSystemClock(String root) {
        return classes()
                .that(inPureLayers(root))
                .should(notAccess("read or wait on the system clock", DomainPurityRules::readsTheClock)
                        .and(notDependOn("a legacy date type or a clock", DomainPurityRules::isAClock)))
                .because("DL §3.9 rule 2: now and today are parameters (NFR-13, NFR-175)");
    }

    /** Rule 3: no ID generation. */
    static ArchRule noIdGeneration(String root) {
        return classes()
                .that(inPureLayers(root))
                .should(notAccess("generate a random UUID", DomainPurityRules::generatesAnId))
                .because("DL §3.9 rule 3: factories take the new ID from IdGenerator");
    }

    /** Rule 4: no randomness. */
    static ArchRule noRandomness(String root) {
        return classes()
                .that(inPureLayers(root))
                .should(notDependOn("a source of randomness", DomainPurityRules::isRandomness)
                        .and(notAccess("call a random method", DomainPurityRules::callsRandomness)))
                .because("DL §3.9 rule 4: the domain uses no randomness");
    }

    /** Rule 4: no I/O. */
    static ArchRule noIo(String root) {
        return classes()
                .that(inPureLayers(root))
                .should(notDependOn("an I/O type", DomainPurityRules::isIo)
                        .and(notAccess(
                                "use the process's streams, environment, system properties or default locale",
                                DomainPurityRules::usesTheProcess))
                        .and(notAccess(
                                "change case, format or build a date pattern with the default locale",
                                DomainPurityRules::readsTheDefaultLocaleImplicitly))
                        .and(notAccess("read a resource from the classpath", DomainPurityRules::readsAResource))
                        .and(notDependOn("a reflection type", DomainPurityRules::isReflection))
                        .and(notAccess("load a class by name", DomainPurityRules::loadsAClassByName))
                        .and(notAccess(
                                "start a thread, an executor, a pool or a parallel stream",
                                DomainPurityRules::startsAThread)))
                .because("DL §3.9 rule 4: the domain does no I/O; the default locale, system properties, the"
                        + " classpath, reflection and the process's threads count as I/O, as a Process does");
    }

    /** Rule 4: no logging. */
    static ArchRule noLogging(String root) {
        return classes()
                .that(inPureLayers(root))
                .should(notDependOn("a logging type", DomainPurityRules::isLogging))
                .because("DL §3.9 rule 4: the domain does no logging");
    }

    /** Rule 5: no generic exceptions. */
    static ArchRule noGenericExceptions(String root) {
        return classes()
                .that(inPureLayers(root))
                .should(notAccess(
                        "create an IllegalArgumentException or IllegalStateException",
                        DomainPurityRules::createsAGenericException))
                .because("DL §3.8 and §3.9 rule 5: a refusal is a named error");
    }

    /**
     * Rule 6: only persistence adapters read the snapshots read under a lock. Its other half, that
     * only they rebuild an aggregate, went with {@code restore}: only Hibernate rebuilds one now
     * (ADR-126, which amends ADR-040's rule 6).
     */
    static ArchRule lockReadsOnlyFromPersistence(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .resideOutsideOfPackage(PERSISTENCE_ADAPTER)
                .should(notAccess("call a readUnderLock factory", DomainPurityRules::readsUnderALock))
                .because("DL §5 and §3.9 rule 6: only persistence adapters call readUnderLock");
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

    /** The kernel and every domain and events package under a root, with their subpackages. */
    static String[] pureLayers(String root) {
        return new String[] {root + "." + KERNEL + "..", root + ".*." + DOMAIN + "..", root + ".*." + EVENTS + ".."};
    }

    /**
     * The classes the purity rules hold: those in {@link #pureLayers}, apart from those in, or
     * below, {@code shared.adapter.in.<x>} and {@code shared.adapter.out.<x>}, the adapters every
     * module shares, which ADR-126 and ADR-130 put beside the kernel without making them part of
     * it: persistence, the clock and the IDs. Any other package below the kernel, {@code
     * shared.adapter.util} or a class directly in {@code shared.adapter.out} among them, is still
     * held.
     */
    static DescribedPredicate<JavaClass> inPureLayers(String root) {
        return JavaClass.Predicates.resideInAnyPackage(pureLayers(root))
                .and(DescribedPredicate.not(DescribedPredicate.describe(
                        "a shared inbound or outbound adapter",
                        type -> isASharedAdapter(root, type.getPackageName()))));
    }

    /** Whether a package lies in, or below, {@code shared.adapter.in.<x>} or {@code shared.adapter.out.<x>}. */
    private static boolean isASharedAdapter(String root, String packageName) {
        String sharedAdapter = root + "." + KERNEL + "." + ADAPTER + ".";
        return SHARED_ADAPTER_DIRECTIONS.stream()
                .anyMatch(direction -> packageName.startsWith(sharedAdapter + direction + "."));
    }

    private static ArchCondition<JavaClass> dependOnlyOnAllowedPackages(String root, String moduleAnnotations) {
        return new ArchCondition<>("depend only on the packages DL §3.9 allows") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                List<String> allowed = allowedPackages(root, moduleAnnotations, item);
                Set<String> allowedTypes = allowedTypes(root, moduleAnnotations, item);
                boolean mayCarryMapping = mayCarryPersistenceAnnotations(root, item);
                List<String> sharedAdapters = List.of(root + "." + KERNEL + "." + ADAPTER);
                item.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> {
                            JavaClass target = baseType(dependency.getTargetClass());
                            if (target.isPrimitive()) {
                                return false;
                            }
                            // The kernel's prefix below would let the shared adapters through.
                            return isInAny(target.getPackageName(), sharedAdapters)
                                    || (!isInAny(target.getPackageName(), allowed)
                                            && !allowedTypes.contains(target.getName())
                                            && !(mayCarryMapping && isAPersistenceAnnotation(target)));
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
     * module annotations; the kernel's application package-info takes one of them only, through
     * {@link #allowedTypes}.
     */
    private static List<String> allowedPackages(String root, String moduleAnnotations, JavaClass item) {
        String[] segments = segmentsBelow(root, item);
        List<String> allowed = new ArrayList<>(ALWAYS_ALLOWED);
        allowed.add(root + "." + KERNEL);
        boolean packageInfo = item.getSimpleName().equals(PACKAGE_INFO);
        if (segments[0].equals(KERNEL)) {
            allowed.addAll(KERNEL_LIBRARIES);
            boolean kernelRoot = segments.length == 1;
            if (packageInfo && kernelRoot) {
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

    /**
     * The single types one class may depend on beyond {@link #allowedPackages}: the kernel's
     * application package-info may carry {@code @NamedInterface}, which makes it a named interface
     * of the kernel (ADR-120 decision 2), and no other module annotation, since it is no module.
     */
    private static Set<String> allowedTypes(String root, String moduleAnnotations, JavaClass item) {
        String[] segments = segmentsBelow(root, item);
        boolean kernelApplication =
                segments.length == 2 && segments[0].equals(KERNEL) && segments[1].equals(APPLICATION);
        return kernelApplication && item.getSimpleName().equals(PACKAGE_INFO)
                ? Set.of(moduleAnnotations + "." + NAMED_INTERFACE)
                : Set.of();
    }

    /**
     * ADR-126: the kernel's own package and every module's domain may carry the persistence
     * annotations that map them; an events package, the kernel's application package and a {@code
     * package-info} may not.
     */
    private static boolean mayCarryPersistenceAnnotations(String root, JavaClass item) {
        if (item.getSimpleName().equals(PACKAGE_INFO)) {
            return false;
        }
        String[] segments = segmentsBelow(root, item);
        boolean kernelRoot = segments.length == 1 && segments[0].equals(KERNEL);
        boolean domain = segments.length > 1 && segments[1].equals(DOMAIN);
        return kernelRoot || domain;
    }

    /**
     * An annotation of Jakarta Persistence, or an enum one takes as a value, or one of the four
     * Hibernate and Spring Data annotations ADR-126 allows.
     */
    private static boolean isAPersistenceAnnotation(JavaClass type) {
        boolean jakarta = type.getPackageName().equals(JAKARTA_PERSISTENCE) && (type.isAnnotation() || type.isEnum());
        return jakarta || PERSISTENCE_ANNOTATIONS.contains(type.getName());
    }

    /**
     * The class's package segments below the root, such as {@code shared} and {@code application}.
     */
    private static String[] segmentsBelow(String root, JavaClass item) {
        return item.getPackageName().substring(root.length() + 1).split("\\.");
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
                || (owner.startsWith("java.time.") && name.equals("dateNow"))
                || (target.getOwner().isAssignableTo(ZoneId.class) && name.equals("systemDefault"))
                || (owner.equals("java.util.TimeZone") && name.equals("getDefault"))
                || (owner.equals("java.lang.Thread") && name.equals("sleep"))
                || (owner.equals("java.util.concurrent.TimeUnit") && name.equals("sleep"));
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

    /** A subclass counts too, such as a PropertyResourceBundle. */
    private static boolean isIo(JavaClass type) {
        if (IO_EXCEPTIONS.contains(type.getName())) {
            return false;
        }
        return isInAny(type.getPackageName(), IO_PACKAGES)
                || isAnyOf(type, IO_CLASSES)
                || IO_CLASSES.stream().anyMatch(type::isAssignableTo);
    }

    private static boolean usesTheProcess(JavaAccess<?> access) {
        String owner = access.getTarget().getOwner().getName();
        String name = access.getTarget().getName();
        return (owner.equals("java.lang.System") && IO_SYSTEM_MEMBERS.contains(name))
                || name.equals(PROPERTY_READERS.get(owner))
                || (owner.equals("java.util.Locale") && DEFAULT_LOCALE_MEMBERS.contains(name));
    }

    private static boolean readsTheDefaultLocaleImplicitly(JavaAccess<?> access) {
        if (!(access.getTarget() instanceof CodeUnitAccessTarget target)) {
            return false;
        }
        String owner = target.getOwner().getName();
        String name = target.getName();
        List<JavaClass> parameters = target.getRawParameterTypes();
        if (owner.equals(DATE_TIME_FORMATTER)) {
            return (name.equals(OF_PATTERN) && parameters.size() == 1) || name.startsWith(OF_LOCALIZED);
        }
        if (owner.equals(DATE_TIME_FORMATTER_BUILDER)) {
            return name.equals(TO_FORMATTER) && parameters.isEmpty();
        }
        if (LOCALE_FORMATS.stream().anyMatch(target.getOwner()::isAssignableTo)) {
            return choosesALocale(target)
                    && parameters.stream().noneMatch(parameter -> LOCALE_CARRIERS.contains(parameter.getName()));
        }
        return owner.equals(STRING)
                && ((CASE_CHANGES.contains(name) && parameters.isEmpty())
                        || name.equals(FORMATTED)
                        || (name.equals(FORMAT)
                                && !parameters.isEmpty()
                                && parameters.get(0).getName().equals(STRING)));
    }

    /**
     * A constructor, a {@code get*Instance} factory, or MessageFormat's static {@code format}: the
     * members of a java.text format that pick the locale it is built for.
     */
    private static boolean choosesALocale(CodeUnitAccessTarget target) {
        String name = target.getName();
        return name.equals(CONSTRUCTOR) || (isStatic(target) && (name.endsWith(FACTORY_SUFFIX) || name.equals(FORMAT)));
    }

    /** A subclass of ClassLoader counts too. */
    private static boolean readsAResource(JavaAccess<?> access) {
        JavaClass owner = access.getTarget().getOwner();
        return RESOURCE_MEMBERS.contains(access.getTarget().getName())
                && RESOURCE_OWNERS.stream().anyMatch(owner::isAssignableTo);
    }

    private static boolean isReflection(JavaClass type) {
        return isInAny(type.getPackageName(), REFLECTION_PACKAGES) || isAnyOf(type, REFLECTION_CLASSES);
    }

    private static boolean loadsAClassByName(JavaAccess<?> access) {
        return access.getTarget().getOwner().getName().equals(CLASS)
                && access.getTarget().getName().equals(FOR_NAME);
    }

    /**
     * A subclass of Thread counts too, through its call to Thread's constructor. Every member of
     * Executors counts, as the class exists to make thread pools.
     */
    private static boolean startsAThread(JavaAccess<?> access) {
        JavaClass owner = access.getTarget().getOwner();
        String name = access.getTarget().getName();
        return (owner.isAssignableTo(THREAD) && name.equals(CONSTRUCTOR))
                || (owner.getName().equals(THREAD) && THREAD_BUILDERS.contains(name))
                || owner.getName().equals(EXECUTORS)
                || THREAD_POOLS.stream().anyMatch(owner::isAssignableTo)
                || (owner.isAssignableTo(COMPLETION_STAGE) && name.endsWith(ASYNC_SUFFIX))
                || (owner.isAssignableTo(COLLECTION) && name.equals(PARALLEL_STREAM))
                || (owner.isAssignableTo(BASE_STREAM) && name.equals(PARALLEL))
                || (owner.getName().equals(CLEANER) && name.equals(CREATE));
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
     * Matches by name, so a later module's snapshot is covered without editing this rule. A type
     * calling its own factory doesn't widen who can build it, so that is allowed.
     */
    private static boolean readsUnderALock(JavaAccess<?> access) {
        return access.getTarget().getName().equals(RestrictedTypes.READ_UNDER_LOCK)
                && !RestrictedTypes.isWithin(access.getOriginOwner(), access.getTargetOwner());
    }

    /**
     * A constructor or a static method, called or referenced, of an optimizer result or of a type
     * nested in one, from outside that result. A record-shaped or package-private constructor is
     * caught here too; {@link DomainShapeRules} also requires the constructors to be private.
     */
    private static boolean buildsAnOptimizerResult(JavaAccess<?> access) {
        AccessTarget target = access.getTarget();
        Optional<JavaClass> result = RestrictedTypes.optimizerResultOf(target.getOwner());
        return access instanceof JavaCodeUnitAccess<?>
                && result.isPresent()
                && (target.getName().equals(CONSTRUCTOR) || isStatic(target))
                && !RestrictedTypes.isWithin(access.getOriginOwner(), result.get());
    }

    private static boolean isStatic(AccessTarget target) {
        // An unresolvable target is treated as static, so the rule errs toward flagging.
        return target.resolveMember()
                .map(member -> member.getModifiers().contains(JavaModifier.STATIC))
                .orElse(true);
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
