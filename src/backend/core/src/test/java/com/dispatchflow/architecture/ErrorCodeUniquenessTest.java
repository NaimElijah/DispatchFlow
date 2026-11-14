package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Error codes are what both apps translate (NFR-69, NFR-144), so one code must mean one thing
 * across every module. The allow-list names errors that several modules declare with the same
 * meaning, each in its own sealed type.
 */
class ErrorCodeUniquenessTest {

    private static final Set<String> SAME_MEANING_IN_SEVERAL_MODULES =
            Set.of("DATE_IN_PAST", "RETENTION_NOT_OVER", "NOT_AN_ACTIVE_DRIVER");

    /** Every code declared by more than one error type, outside the allow-list, with those types. */
    private static SortedMap<String, List<String>> duplicatedCodes(JavaClasses classes) {
        Map<String, List<String>> typesByCode = classes.stream()
                .filter(type -> type.isAssignableTo(DomainError.class))
                .filter(type -> !type.isInterface() && !type.getModifiers().contains(JavaModifier.ABSTRACT))
                .collect(Collectors.groupingBy(
                        ErrorCodeUniquenessTest::codeOf,
                        TreeMap::new,
                        Collectors.mapping(JavaClass::getName, Collectors.toList())));
        SortedMap<String, List<String>> duplicated = new TreeMap<>();
        typesByCode.forEach((code, types) -> {
            if (types.size() > 1 && !SAME_MEANING_IN_SEVERAL_MODULES.contains(code)) {
                duplicated.put(code, types.stream().sorted().toList());
            }
        });
        return duplicated;
    }

    private static String codeOf(JavaClass type) {
        return DomainError.codeOf(type.reflect().asSubclass(DomainError.class));
    }

    @Test
    void GivenTheProductionErrors_WhenTheirCodesAreCollected_ThenNoCodeIsDeclaredTwice() {
        assertThat(duplicatedCodes(ProductionClasses.ALL)).isEmpty();
    }

    @Test
    void GivenTheProductionClasses_WhenTheirErrorsAreCollected_ThenTheKernelErrorsAreAmongThem() {
        assertThat(ProductionClasses.ALL.stream()
                        .filter(type -> type.isAssignableTo(DomainError.class))
                        .map(JavaClass::getSimpleName))
                .contains("NonexistentLocalTime", "InvalidPhoneNumber");
    }

    @Test
    void GivenTwoModulesDeclaringOneCode_WhenCollected_ThenOnlyTheCodeOffTheAllowListIsReported() {
        JavaClasses fixtures = new ClassFileImporter().importPackages("com.dispatchflow.archfixture");

        assertThat(duplicatedCodes(fixtures))
                .containsOnlyKeys("DATE_FULL")
                .containsEntry(
                        "DATE_FULL",
                        List.of(
                                "com.dispatchflow.archfixture.alpha.domain.AlphaError$DateFull",
                                "com.dispatchflow.archfixture.beta.domain.BetaError$DateFull"));
    }
}
