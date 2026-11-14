package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Pins the kernel's error codes, which both apps translate (NFR-144), and checks that each error
 * has a refusal test named after it ({@code ..._Then<ErrorName>}).
 */
class SharedKernelErrorTest {

    private static List<Class<? extends DomainError>> errorTypes() {
        return Arrays.stream(SharedKernelError.class.getPermittedSubclasses())
                .<Class<? extends DomainError>>map(type -> type.asSubclass(DomainError.class))
                .toList();
    }

    @Test
    void GivenTheSharedKernelErrors_WhenTheirCodesAreListed_ThenTheyAreThePinnedCodes() {
        List<String> codes =
                errorTypes().stream().map(DomainError::codeOf).sorted().toList();

        assertThat(codes)
                .containsExactly(
                        "INVALID_COORDINATES",
                        "INVALID_PHONE_NUMBER",
                        "INVALID_TIME_WINDOW",
                        "NONEXISTENT_LOCAL_TIME",
                        "NON_POSITIVE_QUANTITY",
                        "OUTSIDE_THE_DAY",
                        "PRIORITY_OUT_OF_RANGE");
    }

    @Test
    void GivenTheSharedKernelTests_WhenTheirNamesAreRead_ThenEveryErrorHasARefusalTest() {
        List<String> testNames = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                        .importPackages(SharedKernelError.class.getPackageName())
                        .stream()
                        .map(JavaClass::getMethods)
                        .flatMap(methods -> methods.stream().map(JavaMethod::getName))
                        .collect(Collectors.toList());

        assertThat(errorTypes())
                .allSatisfy(type -> assertThat(testNames)
                        .as("a test named ..._Then%s", type.getSimpleName())
                        .anyMatch(name -> name.endsWith("_Then" + type.getSimpleName())));
    }
}
