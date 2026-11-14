package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Every named error has a refusal test (DL §14): for each concrete error, a test class in its
 * package has a method ending in {@code _Then<ErrorName>}. It looks at every class assignable to
 * {@link DomainError}, not only those under a sealed interface, so a module's error added later is
 * checked without a test of its own.
 */
class ErrorRefusalTestsTest {

    private static final String FIXTURE_ROOT = "com.dispatchflow.archfixture";
    private static final String REFUSAL_SUFFIX = "_Then";

    /** The concrete errors, by binary name, that have no refusal test in their package. */
    private static SortedSet<String> untestedErrors(JavaClasses errorTypes, JavaClasses tests) {
        SortedSet<String> untested = new TreeSet<>();
        errorTypes.stream()
                .filter(type -> type.isAssignableTo(DomainError.class)
                        && !type.isInterface()
                        && !type.getModifiers().contains(JavaModifier.ABSTRACT))
                .filter(error -> testMethodNames(tests, error.getPackageName()).stream()
                        .noneMatch(name -> name.endsWith(REFUSAL_SUFFIX + error.getSimpleName())))
                .forEach(error -> untested.add(error.getName()));
        return untested;
    }

    private static List<String> testMethodNames(JavaClasses tests, String packageName) {
        return tests.stream()
                .filter(test -> test.getPackageName().equals(packageName))
                .flatMap(test -> test.getMethods().stream().map(JavaMethod::getName))
                .toList();
    }

    @Test
    void GivenTheProductionErrors_WhenTheirRefusalTestsAreLookedUp_ThenEveryErrorHasOne() {
        JavaClasses tests = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(ProductionClasses.ROOT);

        assertThat(untestedErrors(ProductionClasses.ALL, tests)).isEmpty();
    }

    @Test
    void GivenTheProductionClasses_WhenErrorsAreListed_ThenTheKernelErrorsAreAmongThem() {
        assertThat(ProductionClasses.ALL.stream()
                        .filter(type -> type.isAssignableTo(DomainError.class) && !type.isInterface())
                        .map(JavaClass::getSimpleName))
                .contains("NonexistentLocalTime", "QuantityOutOfRange");
    }

    @Test
    void GivenFixtureErrorsWithSomeRefusalTests_WhenLookedUp_ThenTheUntestedOnesAreReportedWhereverTheyAre() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);

        assertThat(untestedErrors(fixtures, fixtures))
                .contains(
                        "com.dispatchflow.archfixture.alpha.domain.AlphaError$DateFull",
                        "com.dispatchflow.archfixture.beta.domain.BetaError$BetaOnly",
                        "com.dispatchflow.archfixture.beta.domain.BetaError$Scheduling$SlotGone",
                        "com.dispatchflow.archfixture.alpha.domain.ErrorWithAText",
                        "com.dispatchflow.archfixture.alpha.domain.ErrorInheritingAText")
                .doesNotContain(
                        "com.dispatchflow.archfixture.beta.domain.BetaError$DateFull",
                        "com.dispatchflow.archfixture.beta.domain.BetaError$DateInPast",
                        "com.dispatchflow.archfixture.beta.domain.BetaError$Scheduling$SlotTaken");
    }
}
