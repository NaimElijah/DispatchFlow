package com.dispatchflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

/**
 * The package rules of ADR-118, built for a root package as {@link DomainPurityRules} is: no cycle
 * between a module's domain packages, a domain root that uses none of its subpackages, and
 * {@code @NullMarked} on the kernel and on every domain and events package. {@code com.dispatchflow} checks production code; {@code
 * com.dispatchflow.archfixture} checks the fixtures that prove each rule fires.
 */
final class DomainPackageRules {

    private static final String DOMAIN = "domain";

    private DomainPackageRules() {}

    /**
     * One slice per package under {@code <root>.<module>.domain}, the module's domain root included;
     * every other class is left out. ArchUnit's {@code matching("..domain.(**)")} can't do this: its
     * {@code (**)} never matches the domain root itself, so a cycle between the root and a
     * subpackage would go unseen.
     */
    static SliceAssignment domainPackages(String root) {
        PackageMatcher domain = PackageMatcher.of(root + ".*." + DOMAIN + "..");
        return new SliceAssignment() {
            @Override
            public SliceIdentifier getIdentifierOf(JavaClass type) {
                return domain.matches(type.getPackageName())
                        ? SliceIdentifier.of(type.getPackageName())
                        : SliceIdentifier.ignore();
            }

            @Override
            public String getDescription() {
                return "the packages under " + root + ".<module>." + DOMAIN + ", its root included";
            }
        };
    }

    /** ADR-118 rule 7: no cycle between the packages of a module's domain. */
    static ArchRule domainPackagesAreFreeOfCycles(String root) {
        return slices().assignedFrom(domainPackages(root))
                .should()
                .beFreeOfCycles()
                .because("ADR-118 rule 7: dependencies point from aggregate packages to concept packages to"
                        + " the root, and one aggregate package uses another one way only");
    }

    /**
     * ADR-118 rule 2: no class directly in {@code <root>.<module>.domain} uses a class in a package
     * under it. The cycle rule can't see this when the subpackage never names the root, and
     * {@link DomainPurityRules#onlyAllowedDependencies} lets any class reach its own module's domain
     * subpackages.
     */
    static ArchRule domainRootsUseNoSubpackage(String root) {
        return classes()
                .that()
                .resideInAPackage(root + ".*." + DOMAIN)
                .should(useNoPackageBelowItsOwn())
                .because("ADR-118 rule 2: the domain root holds the error and what several packages use, and"
                        + " imports no subpackage");
    }

    private static ArchCondition<JavaClass> useNoPackageBelowItsOwn() {
        return new ArchCondition<>("use no class in a package under its own") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String below = item.getPackageName() + ".";
                item.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> baseType(dependency.getTargetClass())
                                .getPackageName()
                                .startsWith(below))
                        .forEach(dependency ->
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
            }
        };
    }

    private static JavaClass baseType(JavaClass type) {
        return type.isArray() ? type.getBaseComponentType() : type;
    }

    /**
     * The names of the packages {@link #packagesNotNullMarked} checks: the kernel and every domain
     * and events package under a root, with their subpackages, that holds a class.
     */
    static SortedSet<String> nullMarkedSubjects(String root, JavaClasses classes) {
        return subjects(root, classes).map(JavaPackage::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * ADR-118 rule 11: the names of the kernel, domain and events packages under a root, and their
     * subpackages, whose {@code package-info} is missing or carries no {@code @NullMarked}.
     */
    static SortedSet<String> packagesNotNullMarked(String root, JavaClasses classes) {
        return subjects(root, classes)
                .filter(javaPackage -> !javaPackage.isAnnotatedWith(NullMarked.class))
                .map(JavaPackage::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Stream<JavaPackage> subjects(String root, JavaClasses classes) {
        return classes.that(JavaClass.Predicates.resideInAnyPackage(DomainPurityRules.pureLayers(root))).stream()
                .map(JavaClass::getPackage);
    }
}
