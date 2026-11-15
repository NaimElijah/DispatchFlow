package com.dispatchflow.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fixtures under {@code com.dispatchflow.archfixture}, imported once for every rule test. A
 * rule test evaluates a rule against a few of them, taken from this one import, because importing
 * each fixture on its own resolves the same JDK classes again every time and made the rule tests
 * take most of a minute.
 *
 * <p>Every fixture taken from here is fully imported, with the types its fields name, as the
 * production check's whole-root import is.
 */
final class FixtureClasses {

    static final String ROOT = "com.dispatchflow.archfixture";

    private static final JavaClasses ALL = new ClassFileImporter().importPackages(ROOT);

    private FixtureClasses() {}

    /** Every fixture class. */
    static JavaClasses all() {
        return ALL;
    }

    /** Exactly the given fixtures, without the types nested in them. */
    static JavaClasses only(Class<?>... fixtures) {
        Set<String> names = Arrays.stream(fixtures).map(Class::getName).collect(Collectors.toUnmodifiableSet());
        return ALL.that(DescribedPredicate.describe("one of " + names, type -> names.contains(type.getName())));
    }

    /** The fixtures in one package below the root, such as {@code gamma}, and its subpackages. */
    static JavaClasses inPackage(String subpackage) {
        return ALL.that(JavaClass.Predicates.resideInAPackage(ROOT + "." + subpackage + ".."));
    }
}
