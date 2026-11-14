package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every named error has a refusal test (DL §14): for each sealed error type, a test class in its
 * package has a method ending in {@code _Then<ErrorName>}. One test covers every module, so a
 * module's error added later is checked without a test of its own.
 */
class ErrorRefusalTestsTest {

    private static final String FIXTURE_ROOT = "com.dispatchflow.archfixture";
    private static final String REFUSAL_SUFFIX = "_Then";

    /** The errors, by binary name, that have no refusal test in their sealed type's package. */
    private static SortedSet<String> untestedErrors(JavaClasses errorTypes, JavaClasses tests) {
        SortedSet<String> untested = new TreeSet<>();
        errorTypes.stream()
                .filter(type -> type.isInterface()
                        && type.isAssignableTo(DomainError.class)
                        && !type.isEquivalentTo(DomainError.class)
                        && type.reflect().isSealed())
                .forEach(sealedType -> {
                    List<String> testNames = tests.stream()
                            .filter(test -> test.getPackageName().equals(sealedType.getPackageName()))
                            .flatMap(test -> test.getMethods().stream().map(JavaMethod::getName))
                            .toList();
                    leaves(sealedType.reflect())
                            .filter(error -> testNames.stream()
                                    .noneMatch(name -> name.endsWith(REFUSAL_SUFFIX + error.getSimpleName())))
                            .forEach(error -> untested.add(error.getName()));
                });
        return untested;
    }

    /** The concrete errors under a sealed type, through any nested sealed group. */
    private static Stream<Class<?>> leaves(Class<?> type) {
        if (!type.isSealed()) {
            return Stream.of(type);
        }
        return Arrays.stream(type.getPermittedSubclasses()).flatMap(ErrorRefusalTestsTest::leaves);
    }

    @Test
    void GivenTheProductionErrors_WhenTheirRefusalTestsAreLookedUp_ThenEveryErrorHasOne() {
        JavaClasses tests = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(ProductionClasses.ROOT);

        assertThat(untestedErrors(ProductionClasses.ALL, tests)).isEmpty();
    }

    @Test
    void GivenTheProductionClasses_WhenSealedErrorsAreListed_ThenTheKernelErrorIsAmongThem() {
        assertThat(ProductionClasses.ALL.stream()
                        .filter(type -> type.isInterface() && type.isAssignableTo(DomainError.class))
                        .map(JavaClass::getSimpleName))
                .contains("SharedKernelError");
    }

    @Test
    void GivenFixtureErrorsWithSomeRefusalTests_WhenLookedUp_ThenTheUntestedOnesAreReportedThroughNestedGroups() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);

        assertThat(untestedErrors(fixtures, fixtures))
                .containsExactly(
                        "com.dispatchflow.archfixture.alpha.domain.AlphaError$DateFull",
                        "com.dispatchflow.archfixture.alpha.domain.AlphaError$DateInPast",
                        "com.dispatchflow.archfixture.beta.domain.BetaError$BetaOnly",
                        "com.dispatchflow.archfixture.beta.domain.BetaError$Scheduling$SlotGone");
    }
}
